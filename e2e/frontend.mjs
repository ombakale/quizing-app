import { connect, waitFor } from './cdp.mjs';

const PORT = Number(process.env.CDP_PORT || 9333);
const BASE = process.env.BASE || 'http://localhost:8080';
const stamp = Date.now().toString().slice(-6);
const STUDENT = 'e2e.stu' + stamp;
const ADMIN   = 'e2e.adm' + stamp;
const PASS    = 'password123';
const ADMIN_CODE = process.env.ADMIN_CODE || 'quiz-admin-2026';

const results = [];
const check = (name, ok, detail = '') => {
  results.push({ name, ok, detail });
  console.log((ok ? 'PASS  ' : 'FAIL  ') + name + (detail && !ok ? '  -> ' + detail : ''));
};

const page = await connect(PORT);

/* Collect every console error and every failed request for the whole run. */
const consoleErrors = [];
const badResponses = [];
const requestUrls = new Map();
page.on(msg => {
  if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
    consoleErrors.push(msg.params.args.map(a => a.value ?? a.description ?? '').join(' '));
  }
  if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
    consoleErrors.push(msg.params.entry.text + ' ' + (msg.params.entry.url || ''));
  }
  if (msg.method === 'Network.responseReceived') {
    const { status, url } = msg.params.response;
    if (status >= 400) badResponses.push(status + ' ' + url);
  }
  if (msg.method === 'Network.requestWillBeSent') {
    requestUrls.set(msg.params.requestId, msg.params.request.url);
  }
  if (msg.method === 'Network.loadingFailed') {
    badResponses.push('failed ' + msg.params.errorText + ' ' +
      (requestUrls.get(msg.params.requestId) || '(unknown url)'));
  }
});

const set = (sel, value) => page.evaluate(`(() => {
  const n = document.querySelector(${JSON.stringify(sel)});
  n.value = ${JSON.stringify(value)};
  n.dispatchEvent(new Event('input', { bubbles: true }));
  n.dispatchEvent(new Event('change', { bubbles: true }));
  return true;
})()`);

const click = sel => page.evaluate(`document.querySelector(${JSON.stringify(sel)}).click(), true`);
const text  = sel => page.evaluate(`(document.querySelector(${JSON.stringify(sel)})||{}).textContent ?? null`);
const count = sel => page.evaluate(`document.querySelectorAll(${JSON.stringify(sel)}).length`);
const hidden = sel => page.evaluate(`document.querySelector(${JSON.stringify(sel)}).hidden`);

/* Whether the element actually paints. `.alert` and `.error` set display:flex,
   which beats the browser's own [hidden] rule, so an element can read
   hidden === true and still be on screen as an empty coloured box. Only a
   measured box catches that. */
/* Waits for a named quiz tile to render, then starts it. The list is fetched after
   load, and on a persistent database the dashboard also renders an attempt-history
   card that has no .card-title - so both the wait and the scope matter. */
const startQuizNamed = async title => {
  await waitFor(async () => await page.evaluate(`[...document.querySelectorAll('#quiz-list .card-title')]
    .some(n => n.textContent === ${JSON.stringify(title)})`), 'quiz tile "' + title + '"');
  await page.evaluate(`[...document.querySelectorAll('#quiz-list .card')].find(c =>
    c.querySelector('.card-title').textContent === ${JSON.stringify(title)}).querySelector('button').click(), true`);
};

const painted = sel => page.evaluate(`(() => {
  const n = document.querySelector(${JSON.stringify(sel)});
  if (!n) return false;
  const r = n.getBoundingClientRect();
  return r.height > 0 && r.width > 0 && getComputedStyle(n).display !== 'none';
})()`);

/* ---------------------------------------------------------------- 1. login screen */
// Start from a clean slate: a leftover session would redirect straight past the form.
await page.goto(BASE + '/');
await page.evaluate('localStorage.clear(), sessionStorage.clear(), true');
await page.goto(BASE + '/');
check('index.html serves at /', (await text('h1, .pill')) !== null);

// A collapsed alert or field error must not paint. `.alert`/`.error` set
// display:flex, which overrides the browser's [hidden] rule unless the
// stylesheet puts it back.
check('a collapsed form-level alert does not paint', (await painted('#form-error')) === false);
check('an unfilled field error does not paint',
  (await painted('#username-error')) === false && (await painted('#password-error')) === false);

// empty submit -> per-field errors from the client rules
await click('#auth-form button[type=submit]');
check('empty login shows per-field errors',
  (await hidden('#username-error')) === false && (await hidden('#password-error')) === false,
  'username/password errors stayed hidden');
check('a triggered field error does paint', await painted('#username-error'));
check('invalid inputs get aria-invalid',
  await page.evaluate(`document.querySelector('#username').getAttribute('aria-invalid') === 'true'`));
check('error is wired to the input with aria-describedby',
  await page.evaluate(`document.querySelector('#username').getAttribute('aria-describedby') === 'username-error'`));

// password rule mirrors the server's 6 characters, not the old minlength=4
await set('#username', 'someone');
await set('#password', '12345');
await click('#auth-form button[type=submit]');
check('client password rule is 6 characters',
  (await text('#password-error')) === 'Password must be at least 6 characters',
  await text('#password-error'));

// bad credentials -> the server's own message, form-level
await set('#password', 'definitely-wrong');
await click('#auth-form button[type=submit]');
await waitFor(async () => (await hidden('#form-error')) === false, 'form-level error');
check('bad login shows the server message',
  /Invalid username or password/i.test(await text('#form-error')),
  await text('#form-error'));

/* ---------------------------------------------------------------- 2. register admin */
await click('#tab-register');
await set('#username', ADMIN);
await set('#password', PASS);
await set('#role', 'ADMIN');
check('admin code field appears for the Admin role', (await hidden('#admincode-field')) === false);

// wrong code -> 403 with the server's message, not a client-side guess
await set('#adminCode', 'not-the-code');
await click('#auth-form button[type=submit]');
await waitFor(async () => /admin registration code/i.test(await text('#form-error')), '403 message');
check('a wrong admin code is rejected by the server',
  /Invalid admin registration code/i.test(await text('#form-error')),
  await text('#form-error'));

await set('#adminCode', ADMIN_CODE);
await click('#auth-form button[type=submit]');
await page.waitForPath('/dashboard.html');
check('registering as admin lands on the dashboard', true);
check('navbar shows the role as "Admin", never the wire enum',
  (await text('.navbar-identity .badge')) === 'Admin' &&
  !/ADMIN|USER/.test(await text('.navbar')),
  await text('.navbar'));
const dashActions = await page.evaluate(`[...document.querySelectorAll('#dash-actions a')].map(a => a.textContent)`);
check('admin sees the authoring and user-management entry points',
  dashActions.includes('Create Quiz') && dashActions.includes('Manage Users'), JSON.stringify(dashActions));
check('the dashboard shows no empty error box when nothing failed',
  (await painted('#load-error')) === false);
check('the attempt history stays collapsed until there is one',
  (await painted('#attempts')) === false || (await count('#attempts-body tr')) > 0);

/* ---------------------------------------------------------------- 3. author a quiz */
await page.evaluate(`[...document.querySelectorAll('#dash-actions a')]
  .find(a => a.textContent === 'Create Quiz').click(), true`);
await page.waitForPath('/admin-quiz-builder.html');
check('the builder shows no empty error box on load', (await painted('#form-error')) === false);
check('builder opens with one question and two options',
  (await count('[data-question]')) === 1 && (await count('[data-option]')) === 2);

// submitting empty -> per-field errors, including inside the composer
await click('#builder button[type=submit]');
check('empty builder shows the title error', (await hidden('#title-error')) === false);
check('empty builder flags option text too',
  await page.evaluate(`[...document.querySelectorAll('[data-option] .error')].some(e => !e.hidden)`));

let QUIZ = 'E2E Quiz ' + stamp;   // reassigned when the edit test renames it
await set('#title', QUIZ);
await set('#description', 'Written by the end-to-end check');
await set('[data-question]:nth-of-type(1) .field .input', 'Which keyword defines a class in Java?');
await page.evaluate(`(() => {
  const rows = document.querySelectorAll('[data-question]:nth-of-type(1) [data-option]');
  const vals = ['class', 'struct'];
  rows.forEach((r, i) => {
    const input = r.querySelector('.input');
    input.value = vals[i];
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
  rows[0].querySelector('input[type=radio]').checked = true;
  return true;
})()`);

await click('#add-question');
await set('[data-question]:nth-of-type(2) .field .input', 'Which collection forbids duplicates?');
await page.evaluate(`(() => {
  const rows = document.querySelectorAll('[data-question]:nth-of-type(2) [data-option]');
  const vals = ['ArrayList', 'HashSet'];
  rows.forEach((r, i) => {
    const input = r.querySelector('.input');
    input.value = vals[i];
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
  rows[1].querySelector('input[type=radio]').checked = true;
  return true;
})()`);

check('questions are renumbered as they are added',
  (await text('[data-question]:nth-of-type(2) .repeat-title')) === 'Question 2');

await click('#builder button[type=submit]');
await waitFor(async () => (await count('.toast')) > 0, 'success toast');
check('success is a toast in a polite live region, not alert()',
  (await page.evaluate(`document.querySelector('.toast-region').getAttribute('aria-live')`)) === 'polite');
await page.waitForPath('/dashboard.html');

// The quiz list is fetched after load, so wait for it rather than racing it.
await waitFor(async () => (await count('#quiz-list .card-title')) > 0, 'quiz cards to render');
const cards = await page.evaluate(`[...document.querySelectorAll('#quiz-list .card-title')].map(n => n.textContent)`);
check('the new quiz appears on the dashboard', cards.includes(QUIZ), JSON.stringify(cards));
check('the card shows its question count',
  await page.evaluate(`[...document.querySelectorAll('#quiz-list .card')].some(c =>
    c.querySelector('.card-title')?.textContent === ${JSON.stringify(QUIZ)} &&
    c.querySelector('.help')?.textContent === '2 questions')`));

/* ------------------------------------------------------------ 3b. edit the quiz */
// Editing used to be API-only: the UI could create a quiz and nothing else.
await page.evaluate(`[...document.querySelectorAll('#quiz-list .card')].find(c =>
  c.querySelector('.card-title').textContent === ${JSON.stringify(QUIZ)})
  .querySelector('.actions button:nth-of-type(2)').click(), true`);
await page.waitForPath('/admin-quiz-builder.html');
check('Edit opens the builder with the quiz id in the URL', /[?&]id=\d+/.test(await page.evaluate('location.search')),
  await page.evaluate('location.search'));

await waitFor(async () => (await page.evaluate(`document.querySelector('#title').value`)) !== '', 'quiz to load');
check('the builder switches to edit mode', (await text('#builder-heading')) === 'Edit Quiz');
check('the save button is relabelled', (await text('#create-btn')) === 'Save Changes');
check('the existing title is loaded', (await page.evaluate(`document.querySelector('#title').value`)) === QUIZ);
check('both existing questions are loaded', (await count('[data-question]')) === 2);
// The student endpoint omits `correct` entirely, so a loaded answer key proves the
// edit screen is reading the admin view rather than the student one.
check('the answer key is loaded, so the right option is pre-selected',
  await page.evaluate(`document.querySelectorAll('[data-question]')[0]
    .querySelectorAll('[data-option] input[type=radio]')[0].checked`));

const EDITED = QUIZ + ' v2';
await set('#title', EDITED);
await set('[data-question]:nth-of-type(1) .field .input', 'Which keyword defines a class? (edited)');

// Remove the second question, then add a replacement - exercising DELETE and POST
// alongside the PUT of the first one.
await page.evaluate(`document.querySelectorAll('[data-question]')[1]
  .querySelector('.repeat-head button').click(), true`);
check('removing a question renumbers the rest', (await count('[data-question]')) === 1);

await click('#add-question');
await set('[data-question]:nth-of-type(2) .field .input', 'Added during the edit');
await page.evaluate(`(() => {
  const rows = document.querySelectorAll('[data-question]:nth-of-type(2) [data-option]');
  ['yes', 'no'].forEach((v, i) => {
    const input = rows[i].querySelector('.input');
    input.value = v;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
  // Second option, deliberately: the student flow below answers the first option of
  // every question and the result assertions expect one right and one wrong.
  rows[1].querySelector('input[type=radio]').checked = true;
  return true;
})()`);

await click('#builder button[type=submit]');
await waitFor(async () => (await count('.toast')) > 0, 'update toast');
check('saving an edit confirms with a toast', /updated/i.test(await text('.toast')), await text('.toast'));
await page.waitForPath('/dashboard.html');

QUIZ = EDITED;
await waitFor(async () => await page.evaluate(`[...document.querySelectorAll('#quiz-list .card-title')]
  .some(n => n.textContent === ${JSON.stringify(QUIZ)})`), 'renamed quiz tile');
check('the renamed quiz shows on the dashboard', true);
check('the edit added and removed a question, leaving two',
  await page.evaluate(`[...document.querySelectorAll('#quiz-list .card')].some(c =>
    c.querySelector('.card-title')?.textContent === ${JSON.stringify(QUIZ)} &&
    c.querySelector('.help')?.textContent === '2 questions')`),
  await page.evaluate(`[...document.querySelectorAll('#quiz-list .card')].map(c =>
    c.querySelector('.card-title')?.textContent + ': ' + c.querySelector('.help')?.textContent).join(' | ')`));

/* ------------------------------------------------------- 4. answer key stays hidden */
const quizId = await page.evaluate(`(async () => {
  const t = JSON.parse(localStorage.getItem('quizapp.session')).token;
  const list = await (await fetch('/api/quizzes', { headers: { Authorization: 'Bearer ' + t } })).json();
  return list.find(q => q.title === ${JSON.stringify(QUIZ)}).id;
})()`);
const studentPayload = await page.evaluate(`(async () => {
  const t = JSON.parse(localStorage.getItem('quizapp.session')).token;
  return await (await fetch('/api/quizzes/${quizId}', { headers: { Authorization: 'Bearer ' + t } })).text();
})()`);
check('the quiz payload carries no "correct" field before submitting',
  !studentPayload.includes('correct'), studentPayload.slice(0, 200));

/* ------------------------------------------------------------ 5. take it as a student */
await click('.navbar-identity button');       // Logout
await page.waitForPath('/index.html');
check('logout clears the stored session',
  (await page.evaluate(`localStorage.getItem('quizapp.session')`)) === null);

await click('#tab-register');
await set('#username', STUDENT);
await set('#password', PASS);
await click('#auth-form button[type=submit]');
await page.waitForPath('/dashboard.html');
check('a student does not see the Create Quiz button', (await count('#dash-actions a')) === 0);
check('navbar labels a student "Student"', (await text('.navbar-identity .badge')) === 'Student');

// a student who navigates to the admin screen is sent back
await page.goto(BASE + '/admin-quiz-builder.html');
await page.waitForPath('/dashboard.html');
check('a student is redirected away from the admin builder', true);

await startQuizNamed(QUIZ);
await page.waitForPath('/quiz.html');
await waitFor(async () => (await count('fieldset.question')) === 2, 'questions rendered');
check('the runner renders one card per question', true);
check('progress starts empty', (await text('#progress-label')) === '0 of 2 answered');
check('the runner shows no empty alert boxes on load',
  (await painted('#unanswered')) === false && (await painted('#load-error')) === false);
check('option rows are label-wrapped so the whole row is the target',
  await page.evaluate(`[...document.querySelectorAll('.option')].every(o => o.tagName === 'LABEL')`));
check('every option row is at least 44px tall',
  await page.evaluate(`[...document.querySelectorAll('.option')].every(o => o.getBoundingClientRect().height >= 44)`),
  await page.evaluate(`JSON.stringify([...document.querySelectorAll('.option')].map(o => Math.round(o.getBoundingClientRect().height)))`));

// submitting with gaps is refused and focus moves to the first one
await click('#quiz-form button[type=submit]');
check('submitting with unanswered questions is blocked', await painted('#unanswered'));
check('the blocked message counts the gaps',
  /2 questions are still unanswered/.test(await text('#unanswered')), await text('#unanswered'));
check('focus moves to the first unanswered question',
  await page.evaluate(`document.activeElement.closest('fieldset') === document.querySelector('fieldset.question')`));

// answer the first right and the second wrong
await page.evaluate(`(() => {
  const fs = document.querySelectorAll('fieldset.question');
  fs[0].querySelectorAll('.option input')[0].click();
  fs[1].querySelectorAll('.option input')[0].click();
  return true;
})()`);
check('progress tracks answers', (await text('#progress-label')) === '2 of 2 answered');
check('progress percentage updates', (await text('#progress-pct')) === '100%');

await click('#quiz-form button[type=submit]');
await page.waitForPath('/result.html');
// The score counts up over 320ms, so settle before reading it.
await waitFor(async () => (await text('#result-score')) === '1 / 2', 'score count-up to settle');
check('score is rendered from the submit response', (await text('#result-score')) === '1 / 2',
  await text('#result-score'));
check('percentage is rendered', (await text('#result-pct')) === 'Score: 50%', await text('#result-pct'));
check('the score line uses --score-fg, not the old cyan',
  await page.evaluate(`(() => {
    const c = getComputedStyle(document.querySelector('#result-pct')).color;
    return c !== 'rgb(13, 202, 240)';
  })()`),
  await page.evaluate(`getComputedStyle(document.querySelector('#result-pct')).color`));

check('review renders one card per question', (await count('#review .card')) === 2);
check('the right answer is marked Correct',
  (await page.evaluate(`document.querySelector('#review .card:nth-of-type(1) .option-correct .option-mark').textContent`)).includes('Correct'));
check('a wrong answer shows both "Your answer" and "Correct answer"',
  await page.evaluate(`(() => {
    const card = document.querySelectorAll('#review .card')[1];
    return !!card.querySelector('.option-wrong') && !!card.querySelector('.option-missed');
  })()`));
// The three review states must be visually distinct, not all accent blue.
const reviewColours = await page.evaluate(`(() => {
  const bg = sel => {
    const n = document.querySelector(sel);
    return n ? getComputedStyle(n).backgroundColor : null;
  };
  return { correct: bg('.option-correct'), wrong: bg('.option-wrong'), missed: bg('.option-missed'),
           accentRow: getComputedStyle(document.querySelector('.option-correct')).borderTopColor };
})()`);
check('correct and wrong review rows do not share a background',
  reviewColours.correct !== reviewColours.wrong, JSON.stringify(reviewColours));
check('the correct row is not painted accent blue',
  reviewColours.correct !== 'rgb(219, 234, 254)' && !/13, 110, 253/.test(reviewColours.correct || ''),
  JSON.stringify(reviewColours));
check('review states carry an icon, not colour alone',
  await page.evaluate(`[...document.querySelectorAll('#review .option-mark')].every(m => !!m.querySelector('svg'))`));

/* ------------------------------------------------------------- 6. attempt history */
await click('#back-btn');
await page.waitForPath('/dashboard.html');
await waitFor(async () => (await hidden('#attempts')) === false, 'attempt history');
check('the attempt appears in the history table',
  (await page.evaluate(`document.querySelector('#attempts-body tr td').textContent`)) === QUIZ);
check('the history shows the score', 
  (await page.evaluate(`document.querySelectorAll('#attempts-body tr td')[1].textContent`)) === '1 / 2');

/* ------------------------------------------------------- 7. reduced motion */
await page.send('Emulation.setEmulatedMedia',
  { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
await page.goto(BASE + '/quiz.html');
await page.waitForPath('/dashboard.html');    // the attempt was cleared, so back to the list
await startQuizNamed(QUIZ);
await page.waitForPath('/quiz.html');
await waitFor(async () => (await count('fieldset.question')) === 2, 'questions rendered');
await page.evaluate(`(() => {
  document.querySelectorAll('fieldset.question').forEach(f => f.querySelector('.option input').click());
  return true;
})()`);
await click('#quiz-form button[type=submit]');
await page.waitForPath('/result.html');
check('prefers-reduced-motion skips the count-up and shows the final score at once',
  (await text('#result-score')) === '1 / 2', await text('#result-score'));
await page.send('Emulation.setEmulatedMedia', { features: [] });

/* ------------------------------------------------------- 7b. manage users */
// Back to the admin, who is the only one allowed on this screen.
await click('.navbar-identity button');
await page.waitForPath('/index.html');
await set('#username', ADMIN);
await set('#password', PASS);
await click('#auth-form button[type=submit]');
await page.waitForPath('/dashboard.html');

await waitFor(async () => (await count('#dash-actions a')) === 2, 'admin dashboard actions');
await page.evaluate(`[...document.querySelectorAll('#dash-actions a')]
  .find(a => a.textContent === 'Manage Users').click(), true`);
await page.waitForPath('/admin-users.html');
await waitFor(async () => (await count('#users-body tr')) > 0, 'user rows');

const userRow = name => `[...document.querySelectorAll('#users-body tr')]
  .find(tr => tr.querySelector('td').textContent === ${JSON.stringify('%s')})`.replace('%s', name);

check('the account list shows both accounts',
  await page.evaluate(`!!${userRow(ADMIN)} && !!${userRow(STUDENT)}`));
check('roles read as Admin and Student, never the wire enum',
  (await page.evaluate(`${userRow(ADMIN)}.querySelectorAll('td')[1].textContent`)) === 'Admin' &&
  (await page.evaluate(`${userRow(STUDENT)}.querySelectorAll('td')[1].textContent`)) === 'Student');
check('the student\'s submitted attempt is counted',
  Number(await page.evaluate(`${userRow(STUDENT)}.querySelectorAll('td')[2].textContent`)) >= 1,
  await page.evaluate(`${userRow(STUDENT)}.querySelectorAll('td')[2].textContent`));
check('you cannot delete the account you are signed in as',
  (await page.evaluate(`${userRow(ADMIN)}.querySelectorAll('button').length`)) === 0 &&
  /Signed in as you/.test(await page.evaluate(`${userRow(ADMIN)}.querySelectorAll('td')[3].textContent`)));

// Delete is destructive, so it asks first - inline, not a blocking window.confirm().
await page.evaluate(`${userRow(STUDENT)}.querySelector('button').click(), true`);
check('deleting asks for confirmation first',
  /Delete .*\?/.test(await page.evaluate(`${userRow(STUDENT)}.querySelectorAll('td')[3].textContent`)),
  await page.evaluate(`${userRow(STUDENT)}.querySelectorAll('td')[3].textContent`));
check('cancel is focused, so the safe choice is under your hand',
  (await page.evaluate(`document.activeElement.textContent`)) === 'Cancel');

await page.evaluate(`[...${userRow(STUDENT)}.querySelectorAll('button')]
  .find(b => b.textContent === 'Delete').click(), true`);
await waitFor(async () => await page.evaluate(`!${userRow(STUDENT)}`), 'student row to disappear');
check('the account is gone from the list', true);

const studentLogin = await page.evaluate(`(async () => {
  const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: ${JSON.stringify(STUDENT)}, password: ${JSON.stringify(PASS)} }) });
  return r.status;
})()`);
check('the deleted account can no longer sign in', studentLogin === 401, 'login returned ' + studentLogin);

/* ------------------------------------------------------- 7c. delete a quiz */
await page.goto(BASE + '/dashboard.html');
await waitFor(async () => await page.evaluate(`[...document.querySelectorAll('#quiz-list .card-title')]
  .some(n => n.textContent === ${JSON.stringify(QUIZ)})`), 'quiz tile');
// Scope every step to this one card. Against a database with other quizzes in it,
// a document-wide search for a "Delete" button finds the first tile's, not this one's.
const QUIZ_CARD = `[...document.querySelectorAll('#quiz-list .card')].find(c =>
  c.querySelector('.card-title').textContent === ${JSON.stringify(QUIZ)})`;

await page.evaluate(`${QUIZ_CARD}.querySelector('.actions button:nth-of-type(3)').click(), true`);
check('deleting a quiz asks for confirmation first',
  /Delete this quiz\?/.test(await page.evaluate(`${QUIZ_CARD}.textContent`)),
  await page.evaluate(`${QUIZ_CARD}.textContent`));
await page.evaluate(`[...${QUIZ_CARD}.querySelectorAll('.actions button')]
  .find(b => b.textContent === 'Delete').click(), true`);
await waitFor(async () => await page.evaluate(`![...document.querySelectorAll('#quiz-list .card-title')]
  .some(n => n.textContent === ${JSON.stringify(QUIZ)})`), 'quiz tile to disappear');
check('the quiz is gone from the dashboard', true);

/* --------------------------------------------------------------- 8. accessibility */
// Clear the session first: with one stored, index.html redirects to the dashboard,
// and the Tab presses below would land mid-navigation on a page with nothing focusable.
await page.goto(BASE + '/index.html');
await page.evaluate('localStorage.clear(), sessionStorage.clear(), true');
await page.goto(BASE + '/index.html');
await waitFor(() => page.evaluate(`!!document.querySelector('#auth-form button[type=submit]')`), 'login form');

// Tab from the address bar into the page and confirm a visible ring on each stop.
const rings = [];
for (let i = 0; i < 6; i++) {
  await page.key('Tab', 'Tab', 9);
  rings.push(await page.evaluate(`(() => {
    const a = document.activeElement;
    if (!a || a === document.body) return null;
    const s = getComputedStyle(a);
    return { tag: a.tagName, id: a.id,
             ring: (s.boxShadow && s.boxShadow !== 'none') || (s.outlineStyle !== 'none' && s.outlineWidth !== '0px') };
  })()`));
}
const focusable = rings.filter(Boolean);
check('every keyboard stop shows a focus ring',
  focusable.length > 0 && focusable.every(r => r.ring),
  JSON.stringify(focusable));

/* ---------------------------------------------------- 8b. nothing overflows its card */
/* Without `box-sizing: border-box`, `width: 100%` plus padding and border made every
   input 30px wider than the card around it. Measuring the boxes is the only way to
   catch that - the DOM looks entirely correct. */
const overflows = await page.evaluate(`(() => {
  const bad = [];
  for (const field of document.querySelectorAll('.input, .select, .textarea, .btn-block')) {
    const card = field.closest('.card');
    if (!card) continue;
    const f = field.getBoundingClientRect(), c = card.getBoundingClientRect();
    // A collapsed field (the admin-code input on the login tab) measures 0x0 and
    // has no position to compare - it is not on screen to overflow anything.
    if (f.width === 0 && f.height === 0) continue;
    // A one-pixel tolerance for sub-pixel rounding at fractional zoom.
    if (f.right > c.right + 1 || f.left < c.left - 1) {
      bad.push((field.id || field.className) + ' by ' + Math.round(f.right - c.right) + 'px');
    }
  }
  return bad;
})()`);
check('no form control overflows the card holding it', overflows.length === 0, JSON.stringify(overflows));

const tooTall = await page.evaluate(`(() => {
  // min-height is 44px; content-box sizing used to render these at 78px.
  return [...document.querySelectorAll('.input')]
    .map(n => Math.round(n.getBoundingClientRect().height))
    .filter(h => h > 56);
})()`);
check('inputs are sized to their min-height, not inflated by padding',
  tooTall.length === 0, JSON.stringify(tooTall));

/* ------------------------------------------------------------------ 9. narrow viewport */
await page.send('Emulation.setDeviceMetricsOverride',
  { width: 375, height: 700, deviceScaleFactor: 1, mobile: true });
for (const path of ['/index.html', '/dashboard.html']) {
  await page.goto(BASE + path);
  await new Promise(r => setTimeout(r, 300));
  const overflow = await page.evaluate(`document.documentElement.scrollWidth - document.documentElement.clientWidth`);
  check('no horizontal scroll at 375px on ' + path, overflow <= 0, 'overflow ' + overflow + 'px');
}
await page.send('Emulation.clearDeviceMetricsOverride');

/* -------------------------------------------------------------------- 10. hygiene */
// The bad-login and bad-admin-code attempts above are deliberate, and the browser
// logs every 4xx fetch itself. Those two are expected; anything else is not.
const expected = /\/api\/auth\/(login|register)/;
const realErrors = consoleErrors.filter(e => e && !/favicon/i.test(e) && !expected.test(e));
check('no console errors during the whole run', realErrors.length === 0, JSON.stringify(realErrors));
const realBad = badResponses.filter(r => !/\/api\//.test(r));   // deliberate 401/403/400 above
check('no 4xx or 5xx on any static asset', realBad.length === 0, JSON.stringify(realBad));

/* ------------------------------------------------------------------- cleanup
   On the in-memory H2 profile this is pointless - the next restart clears
   everything. Against the deployed Postgres it is not: every run would otherwise
   leave another "E2E Quiz" tile on the dashboard for real users to see.

   The two throwaway accounts stay behind. The API has no delete-user endpoint,
   which is a real gap rather than something to work around here. */
const cleaned = await page.evaluate(`(async () => {
  const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: ${JSON.stringify(ADMIN)}, password: ${JSON.stringify(PASS)} }) });
  if (!r.ok) return 'could not log back in as admin';
  const { token } = await r.json();
  const auth = { Authorization: 'Bearer ' + token };
  // Throwaway accounts from this run and any earlier one. Not this admin: the API
  // refuses to delete the account making the call, and the next run sweeps it up.
  const users = await (await fetch('/api/admin/users', { headers: auth })).json();
  for (const u of users) {
    if (u.username.startsWith('e2e.') && u.username !== ${JSON.stringify(ADMIN)}) {
      await fetch('/api/admin/users/' + u.id, { method: 'DELETE', headers: auth });
    }
  }

  const list = await (await fetch('/api/quizzes', { headers: auth })).json();
  // Every "E2E Quiz ..." tile, not just this run's: a run that fails partway never
  // reaches this point, so the next successful one sweeps up what it left behind.
  const mine = list.filter(q => q.title.startsWith('E2E Quiz '));
  for (const q of mine) await fetch('/api/admin/quizzes/' + q.id, { method: 'DELETE', headers: auth });
  return 'removed ' + mine.length + ' quiz(zes)';
})()`);
check('no E2E quizzes are left behind', /^removed \d/.test(cleaned), cleaned);

page.close();
const failed = results.filter(r => !r.ok);
console.log('\n' + (results.length - failed.length) + '/' + results.length + ' checks passed');
if (failed.length) {
  console.log('\nFailures:');
  for (const f of failed) console.log('  - ' + f.name + ': ' + f.detail);
  process.exit(1);
}
