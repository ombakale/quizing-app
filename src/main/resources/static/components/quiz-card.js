/* QuizCard — a tile in the Available Quizzes grid.

   Built from QuizSummaryResponse: { id, title, description, totalQuestions }.
   The list endpoint deliberately omits questions and options so answer keys are
   never exposed, which is why the count comes from totalQuestions rather than
   from a questions array.

   The CTA is outline, not solid: the page's one primary belongs elsewhere.
   Title and description come straight from the API and are set with
   .textContent — the previous frontend interpolated them into innerHTML. */

function QuizCard({ quiz, onStart, onEdit, onDelete }) {
  const count = quiz.totalQuestions;

  // Start Quiz stays the same for everyone. Admin management lives on a second row
  // so the primary reading of a tile does not change with who is looking at it.
  const actions = el('div', { class: 'actions', style: 'gap:var(--space-2)' },
    el('button', { class: 'btn btn-outline btn-sm', type: 'button',
                   onclick: () => onStart(quiz.id), text: 'Start Quiz' }));

  if (onEdit) {
    actions.append(el('button', { class: 'btn btn-ghost btn-sm', type: 'button',
                                  onclick: () => onEdit(quiz.id), text: 'Edit' }));
  }
  if (onDelete) {
    actions.append(el('button', { class: 'btn btn-ghost btn-sm', type: 'button',
      onclick: () => inlineConfirm(actions, {
        message: 'Delete this quiz?',
        onConfirm: ({ restore }) => onDelete(quiz.id, { restore })
      }),
      text: 'Delete' }));
  }

  return el('div', { class: 'card card-interactive' }, [
    el('h2', { class: 'card-title', text: quiz.title }),
    el('p',  { class: 'card-desc', text: quiz.description || 'No description' }),
    el('p',  { class: 'help', style: 'margin:0 0 var(--space-3)',
               text: count + (count === 1 ? ' question' : ' questions') }),
    actions
  ]);
}

/* EmptyState — name what is empty and the single action that fills it. */
function EmptyState({ message, actionLabel, actionHref, note }) {
  return el('div', { class: 'empty', style: 'grid-column:1/-1' }, [
    el('p', { text: message, style: 'margin:0 0 var(--space-3)' }),
    actionHref
      ? el('a', { class: 'btn btn-outline btn-sm', href: actionHref, text: actionLabel })
      : el('p', { class: 'help', text: note || '', style: 'margin:0' })
  ]);
}

Object.assign(window, { QuizCard, EmptyState });
