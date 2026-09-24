/* OptionRow — the product's central control.
   The <label> wraps the input so the whole 44px row is the target. Hover tints
   the background and never greys the label; see .option in app.css. */

function OptionRow({ option, name, checked, onSelect }) {
  const input = el('input', { type: 'radio', name, value: option.id });
  if (checked) input.checked = true;
  if (onSelect) input.addEventListener('change', () => onSelect(option.id));

  return el('label', { class: 'option' }, [
    input,
    el('span', { class: 'option-text', text: option.text })
  ]);
}

/* ReviewOptionRow — post-submission only.
   GET /api/quizzes/{id} omits the `correct` flag on purpose, so these states may
   only be built from QuizResultResponse.details after submitting.
   Colour is never the only signal: each row carries an icon and a text marker. */
function ReviewOptionRow({ option, selectedOptionId, correctOptionId }) {
  const isSelected = option.id === selectedOptionId;
  const isCorrect  = option.id === correctOptionId;

  let variant, icon, marker;
  if (isSelected && isCorrect)       { variant = 'option-correct'; icon = 'check'; marker = 'Correct'; }
  else if (isSelected && !isCorrect) { variant = 'option-wrong';   icon = 'cross'; marker = 'Your answer'; }
  else if (isCorrect)                { variant = 'option-missed';  icon = 'check'; marker = 'Correct answer'; }
  else return null;                  // unchosen, incorrect options add nothing

  const input = el('input', { type: 'radio', disabled: 'disabled' });
  if (isSelected) input.checked = true;

  return el('div', { class: 'option ' + variant }, [
    input,
    el('span', { class: 'option-text', text: option.text }),
    el('span', { class: 'option-mark', html: ICON[icon] }, el('span', { text: marker }))
  ]);
}

Object.assign(window, { OptionRow, ReviewOptionRow });
