/* ============================================================================
   Quiz Application — API client, session and shared helpers.

   Replaces the design kit's kit.js. The kit ran offline against sample data in
   localStorage; this file talks to the real Spring Boot API instead. Everything
   the component factories in components/*.js depend on — el(), ICON, Rules,
   session, requireSession — is still exported here under the same names, so the
   components are unchanged from the kit.

   Two deliberate choices:

   - The base URL is relative. application.yml binds server.port to Cloud Run's
     injected ${PORT}, so any hardcoded host or port breaks on deploy. The
     frontend ships inside the same JAR and is served same-origin, so none of
     these calls are subject to CORS at all. (SecurityConfig does configure CORS,
     but that is for outside callers - swagger editors, gateways - not for this.)

   - DOM is built with el() and .textContent, never `innerHTML +=` with API
     strings. An admin-authored quiz title is untrusted input on a student's
     screen; the previous frontend interpolated it straight into innerHTML.
   ========================================================================= */

const API = '/api';

/* ------------------------------------------------------------------ session
   The JWT is stateless and CSRF is disabled server-side, so there is no cookie
   and no CSRF token to carry — just the bearer token. jwt.expiration defaults
   to 86400000 ms (24 h) and there is no refresh endpoint, so an expired token
   simply surfaces as a 401 and we return to the login screen. */
const SESSION_KEY = 'quizapp.session';

const session = {
  get() {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      return null;                       // private mode or corrupted entry
    }
  },
  set(auth) {
    try {
      localStorage.setItem(SESSION_KEY, JSON.stringify({
        token: auth.token, username: auth.username, role: auth.role
      }));
    } catch (e) { /* the screen still works for this page load */ }
  },
  clear() {
    try {
      localStorage.removeItem(SESSION_KEY);
      sessionStorage.removeItem(ATTEMPT_KEY);
      sessionStorage.removeItem(RESULT_KEY);
    } catch (e) { /* nothing to clean up */ }
  }
};

/* Hand-off between screens. sessionStorage, not localStorage: an in-flight
   attempt belongs to this tab only, and must not outlive the browser session.

   The stored quiz payload is the student view from GET /api/quizzes/{id},
   which has no `correct` field at all — the server strips it. It is kept only
   so the result screen can show option *text* next to the correctOptionId that
   POST .../submit returns. Correctness is never cached before submission. */
const ATTEMPT_KEY = 'quizapp.attempt';
const RESULT_KEY  = 'quizapp.result';

const handoff = {
  /* An attempt is only ever a quiz id plus the answers chosen so far, so a
     reload mid-quiz refetches the questions rather than trusting a stale copy. */
  startAttempt(quizId) {
    sessionStorage.setItem(ATTEMPT_KEY, JSON.stringify({ quizId: Number(quizId), answers: {} }));
  },
  getAttempt() {
    try { return JSON.parse(sessionStorage.getItem(ATTEMPT_KEY) || 'null'); }
    catch (e) { return null; }
  },
  saveAnswers(answers) {
    const attempt = handoff.getAttempt();
    if (!attempt) return;
    attempt.answers = answers;
    sessionStorage.setItem(ATTEMPT_KEY, JSON.stringify(attempt));
  },

  /* The result screen needs option *text*, which QuizResultResponse.details
     does not carry, so the student quiz payload rides along. That payload is
     the one from GET /api/quizzes/{id} and has no `correct` field in it. */
  setResult(result, quiz) { sessionStorage.setItem(RESULT_KEY, JSON.stringify({ result, quiz })); },
  getResult() {
    try { return JSON.parse(sessionStorage.getItem(RESULT_KEY) || 'null'); }
    catch (e) { return null; }
  },

  clear() {
    sessionStorage.removeItem(ATTEMPT_KEY);
    sessionStorage.removeItem(RESULT_KEY);
  }
};

/* -------------------------------------------------------------- api client
   Every failure from this API arrives in one shape:
     { timestamp, status, error, message, path, fieldErrors? }
   fieldErrors is @JsonInclude(NON_NULL), so it is present only on validation
   failures. ApiError carries both branches and the caller decides which to
   render — per field, or form-level. */
class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors || null;
  }
}

async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {};
  const current = session.get();
  if (auth && current) headers['Authorization'] = 'Bearer ' + current.token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(API + path, {
      method, headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch (e) {
    // Network-level failure: there is no ErrorResponse to show, so say so plainly.
    throw new ApiError(0, 'Could not reach the server. Check your connection and try again.');
  }

  // An authenticated call rejected with 401 means the token is gone or expired.
  if (response.status === 401 && auth && current) {
    session.clear();
    location.href = 'index.html';
    throw new ApiError(401, 'Your session has expired. Please sign in again.');
  }

  if (response.status === 204) return null;

  let payload = null;
  const type = response.headers.get('content-type') || '';
  if (type.includes('application/json')) {
    try { payload = await response.json(); } catch (e) { payload = null; }
  }

  if (!response.ok) {
    // Show what the server said. Never substitute a friendlier message.
    const message = (payload && payload.message) || (response.status + ' ' + response.statusText);
    throw new ApiError(response.status, message, payload && payload.fieldErrors);
  }
  return payload;
}

/* Endpoint map — mirrors source-examples/api-contract.md one to one. */
const Api = {
  register: data => api('/auth/register', { method: 'POST', body: data, auth: false }),
  login:    data => api('/auth/login',    { method: 'POST', body: data, auth: false }),

  quizzes:   ()   => api('/quizzes'),
  quiz:      id   => api('/quizzes/' + id),
  submit:    (id, answers) => api('/quizzes/' + id + '/submit', { method: 'POST', body: { answers } }),
  attempts:  ()   => api('/quizzes/attempts'),

  /* Admin reads are a separate path from the student ones: these responses carry the
     answer key, which is exactly why the student endpoints do not. */
  adminQuizzes: ()   => api('/admin/quizzes'),
  adminQuiz:    id   => api('/admin/quizzes/' + id),

  createQuiz: data => api('/admin/quizzes', { method: 'POST', body: data }),
  updateQuiz: (id, data) => api('/admin/quizzes/' + id, { method: 'PUT', body: data }),
  deleteQuiz: id => api('/admin/quizzes/' + id, { method: 'DELETE' }),

  addQuestion:    (quizId, data) => api('/admin/quizzes/' + quizId + '/questions', { method: 'POST', body: data }),
  updateQuestion: (id, data) => api('/admin/questions/' + id, { method: 'PUT', body: data }),
  deleteQuestion: id => api('/admin/questions/' + id, { method: 'DELETE' }),

  users:      ()  => api('/admin/users'),
  deleteUser: id  => api('/admin/users/' + id, { method: 'DELETE' })
};

/* --------------------------------------------------------------- validation
   Mirrors the server's Bean Validation annotations exactly, so the first
   feedback a user gets is not a round-trip 400. The previous frontend shipped
   minlength="4" against a 6-character server rule; that is the bug this table
   exists to prevent.

   adminCode is deliberately absent: the code is a server secret
   (app.admin.registration-code). We check only that something was entered and
   let the server decide, which is why a wrong code arrives as a 403. */
const Rules = {
  username(v) {
    if (!v) return 'Username is required';
    if (v.length < 3 || v.length > 50) return 'Username must be between 3 and 50 characters';
    if (!/^[A-Za-z0-9._-]+$/.test(v)) return 'Username may use letters, numbers, dot, underscore and hyphen only';
    return null;
  },
  password(v) {
    if (!v) return 'Password is required';
    if (v.length < 6 || v.length > 100) return 'Password must be at least 6 characters';
    return null;
  },
  adminCode(v, role) {
    if (role !== 'ADMIN') return null;
    if (!v) return 'Admin registration code is required';
    return null;                        // the server validates the value itself
  },
  quizTitle(v) {
    if (!v) return 'Quiz title is required';
    if (v.length > 200) return 'Quiz title must be at most 200 characters';
    return null;
  },
  quizDescription(v) {
    if (v && v.length > 1000) return 'Quiz description must be at most 1000 characters';
    return null;
  },
  questionText(v) {
    if (!v) return 'Question text is required';
    if (v.length > 1000) return 'Question text must be at most 1000 characters';
    return null;
  },
  optionText(v) {
    if (!v) return 'Option text is required';
    if (v.length > 500) return 'Option text must be at most 500 characters';
    return null;
  }
};

/* ------------------------------------------------------------------ helpers */
const el = (tag, props = {}, children = []) => {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') n.className = v;
    else if (k === 'text') n.textContent = v;          // never innerHTML
    else if (k === 'html') n.innerHTML = v;            // icons only, never data
    else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
    else if (v !== null && v !== false) n.setAttribute(k, v);
  }
  for (const c of [].concat(children)) if (c) n.append(c);
  return n;
};

const ICON = {
  check: '<svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.5 8.5l3.5 3.5 7.5-8"/></svg>',
  cross: '<svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"><path d="M4 4l8 8M12 4l-8 8"/></svg>',
  alert: '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="8" cy="8" r="6.5"/><path d="M8 5v3.5M8 11h.01"/></svg>',
  brand: '<svg width="24" height="24" viewBox="0 0 512 512" fill="none" stroke="currentColor" stroke-width="34" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M 389.9 232.4 A 136 136 0 1 1 302.5 128.2"/><path d="M 176 258 L 234 316 L 372 150"/></svg>'
};

/* Role labels. USER / ADMIN are wire values and must never surface raw — the
   previous frontend leaked "Logged in: admin (ADMIN)" into the navbar. */
const roleLabel = role => (role === 'ADMIN' ? 'Admin' : 'Student');

/* Guard — screens that need a session, optionally a specific role.
   Returns false when it has already started a redirect, so callers can bail. */
function requireSession(role) {
  const current = session.get();
  if (!current) { location.href = 'index.html'; return false; }
  if (role && current.role !== role) { location.href = 'dashboard.html'; return false; }
  return true;
}

/* Component factories live in components/*.js and are loaded after this file. */
Object.assign(window, {
  API, Api, ApiError, api, session, handoff, Rules, el, ICON, roleLabel, requireSession
});
