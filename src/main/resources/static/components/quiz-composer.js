/* QuizComposer — the ADMIN authoring surface.

   Repeatable questions, each with repeatable options and exactly one marked
   correct. Kept out of the screen file so the screen holds only its own wiring,
   and so the composer can be reused for editing an existing quiz.

   Every guard mirrors a server rule: a quiz needs >=1 question, a question
   needs >=2 options and exactly one correct, and text is capped at 1000 / 500.
   collect() returns the QuizRequest shape exactly — no ids, because
   QuizRequest deliberately has no id field: a client-supplied id would turn a
   create into a merge over an existing quiz. */

function QuizComposer(mount) {
  let seq = 0;

  function addOption(list, questionId, value = '', correct = false) {
    const optionId = 'o' + (++seq);
    const input = el('input', { class: 'input', placeholder: 'Option text', maxlength: '500', value });
    const err   = el('p', { class: 'error', id: optionId + '-error', hidden: 'hidden' });
    const radio = el('input', { type: 'radio', name: 'correct_' + questionId,
                                'aria-label': 'Mark this option correct',
                                style: 'accent-color:var(--authoring)' });
    if (correct) radio.checked = true;

    const remove = el('button', { class: 'btn btn-ghost btn-sm', type: 'button', text: 'Remove',
      onclick: () => {
        if (list.querySelectorAll('[data-option]').length <= 2) {
          toast('A question must have at least two options.', 'danger'); return;
        }
        row.remove();
      } });

    const row = el('div', { 'data-option': '1', style: 'margin-bottom:var(--space-2)' }, [
      el('div', { style: 'display:flex;gap:var(--space-2);align-items:center' }, [
        el('label', { class: 'correct-toggle' }, [radio, el('span', { text: 'Correct' })]),
        input, remove
      ]),
      err
    ]);
    row._input = input; row._err = err; row._radio = radio;
    list.append(row);
    return row;
  }

  /* `serverId` is the id of an existing question being edited, or null for a new
     one. It never reaches the payload - it only routes the save to PUT or POST. */
  function addQuestion(text = '', options = null, serverId = null) {
    const questionId = 'q' + (++seq);
    const stem = el('input', { class: 'input', maxlength: '1000', value: text,
      placeholder: 'Question: e.g. What keyword defines a class?' });
    const stemErr = el('p', { class: 'error', id: questionId + '-error', hidden: 'hidden' });
    const list = el('div', {});

    const item = el('div', { class: 'repeat-item', 'data-question': '1' }, [
      el('div', { class: 'repeat-head' }, [
        el('span', { class: 'repeat-title', text: 'Question' }),
        el('button', { class: 'btn btn-ghost btn-sm', type: 'button', text: 'Remove',
          onclick: () => {
            if (mount.querySelectorAll('[data-question]').length <= 1) {
              toast('A quiz must contain at least one question.', 'danger'); return;
            }
            item.remove(); renumber();
          } })
      ]),
      el('div', { class: 'field' }, [stem, stemErr]),
      list,
      el('button', { class: 'btn btn-ghost btn-sm', type: 'button', text: 'Add option',
        onclick: () => addOption(list, questionId) })
    ]);
    item._stem = stem; item._stemErr = stemErr; item._list = list;
    item._serverId = serverId;
    mount.append(item);

    (options || [{ text: '', correct: true }, { text: '', correct: false }])
      .forEach(o => addOption(list, questionId, o.text, o.correct));
    renumber();
    return item;
  }

  function renumber() {
    mount.querySelectorAll('[data-question]').forEach((node, i) => {
      node.querySelector('.repeat-title').textContent = 'Question ' + (i + 1);
    });
  }

  function focusLast() {
    const last = mount.lastElementChild;
    if (last) last.querySelector('input').focus();
  }

  function questionNodes() { return [...mount.querySelectorAll('[data-question]')]; }

  /* Validates every field and returns the QuizRequest-shaped questions array.
     Field errors are written in place; `ok` reports whether it may be sent. */
  function collect() {
    const { ok, questions } = collectForEdit();
    return { ok, questions: questions.map(({ text, options }) => ({ text, options })) };
  }

  /* As collect(), but each question also carries the server id it came from
     (null for one the author just added). */
  function collectForEdit() {
    let ok = true;
    const questions = [];

    for (const item of questionNodes()) {
      const stemValue = item._stem.value.trim();
      ok = setFieldError(item._stem, item._stemErr, Rules.questionText(stemValue)) && ok;

      const options = [];
      let hasCorrect = false;
      for (const row of item._list.querySelectorAll('[data-option]')) {
        const value = row._input.value.trim();
        ok = setFieldError(row._input, row._err, Rules.optionText(value)) && ok;
        if (row._radio.checked) hasCorrect = true;
        options.push({ text: value, correct: row._radio.checked });
      }

      if (options.length < 2) { ok = false; toast('A question must have at least two options.', 'danger'); }
      if (!hasCorrect)        { ok = false; toast('Mark one option as correct.', 'danger'); }
      questions.push({ serverId: item._serverId, text: stemValue, options });
    }

    if (!questions.length) ok = false;
    return { ok, questions };
  }

  /* Server-side validation lands on nested paths — Bean Validation reports
     `questions[0].options[1].text`. Route each one back to the input it came
     from so the admin sees it in place rather than as one long alert.
     Returns the paths it could not place, for the form-level fallback. */
  function applyServerErrors(fieldErrors) {
    const unplaced = [];
    for (const [path, message] of Object.entries(fieldErrors || {})) {
      const match = /^questions\[(\d+)\](?:\.options\[(\d+)\])?\.text$/.exec(path);
      if (!match) { unplaced.push([path, message]); continue; }

      const item = questionNodes()[Number(match[1])];
      if (!item) { unplaced.push([path, message]); continue; }

      if (match[2] === undefined) {
        setFieldError(item._stem, item._stemErr, message);
      } else {
        const row = item._list.querySelectorAll('[data-option]')[Number(match[2])];
        if (row) setFieldError(row._input, row._err, message);
        else unplaced.push([path, message]);
      }
    }
    return unplaced;
  }

  function clear() { mount.textContent = ''; }

  return { addQuestion, focusLast, collect, collectForEdit, applyServerErrors, clear };
}

Object.assign(window, { QuizComposer });
