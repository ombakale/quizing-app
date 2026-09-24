/* Toast — replaces the previous frontend's blocking alert("Quiz created successfully!").
   Announced through a polite live region so it does not interrupt. */

function Toast({ message, kind = 'success' }) {
  return el('div', {
    class: 'alert alert-' + kind + ' toast',
    html: kind === 'success' ? ICON.check : ICON.alert
  }, el('div', { text: message }));
}

function toast(message, kind = 'success') {
  let region = document.querySelector('.toast-region');
  if (!region) {
    region = el('div', { class: 'toast-region', role: 'status', 'aria-live': 'polite' });
    document.body.append(region);
  }
  const node = Toast({ message, kind });
  region.append(node);
  setTimeout(() => node.remove(), 4000);
}

/* ProgressBar — quiz runner completion. */
function updateProgress({ answered, total }) {
  const pct = total ? Math.round((answered / total) * 100) : 0;
  const label = document.getElementById('progress-label');
  const pctEl = document.getElementById('progress-pct');
  const fill  = document.getElementById('progress-fill');
  const bar   = document.getElementById('progress');
  if (label) label.textContent = answered + ' of ' + total + ' answered';
  if (pctEl) pctEl.textContent = pct + '%';
  if (fill)  fill.style.width = pct + '%';
  if (bar)   bar.setAttribute('aria-valuenow', String(pct));
  return pct;
}

/* Inline confirmation for a destructive action.

   Not window.confirm(): that is the blocking dialog this project already replaced
   for success messages, and it cannot be styled, cannot be reached by the design
   system's focus ring, and reads as a browser alert rather than part of the page.

   Swaps the contents of `container` for a question and two buttons, and puts them
   back on cancel. Focus moves to the cancel button, so the safe choice is the one
   under your hand and Escape backs out. */
function inlineConfirm(container, { message, confirmLabel = 'Delete', onConfirm }) {
  const original = [...container.childNodes];

  function restore() {
    container.textContent = '';
    original.forEach(node => container.append(node));
  }

  const cancel = el('button', { class: 'btn btn-ghost btn-sm', type: 'button',
                                text: 'Cancel', onclick: restore });
  const confirm = el('button', { class: 'btn btn-danger btn-sm', type: 'button',
                                 text: confirmLabel,
                                 onclick: () => onConfirm({ restore }) });

  container.textContent = '';
  container.append(
    el('span', { class: 'help', style: 'margin:0', role: 'alert', text: message }),
    cancel, confirm);

  container.addEventListener('keydown', function escape(e) {
    if (e.key === 'Escape') { container.removeEventListener('keydown', escape); restore(); }
  });

  cancel.focus();
}

Object.assign(window, { Toast, toast, updateProgress, inlineConfirm });
