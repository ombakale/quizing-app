/* Form field helpers.

   Renders the two branches of the API's single ErrorResponse shape:
   `fieldErrors` present -> per field; otherwise -> form-level `message`. */

function setFieldError(input, msgEl, message) {
  if (message) {
    input.setAttribute('aria-invalid', 'true');
    input.setAttribute('aria-describedby', msgEl.id);
    msgEl.innerHTML = ICON.alert;                 // icon markup only, never data
    msgEl.append(el('span', { text: message }));
    msgEl.hidden = false;
  } else {
    input.removeAttribute('aria-invalid');
    input.removeAttribute('aria-describedby');
    msgEl.textContent = '';
    msgEl.hidden = true;
  }
  return !message;
}

/* Form-level alert — used when the failure has no fieldErrors (401, 403, 404, 409). */
function showFormError(box, message) {
  box.innerHTML = ICON.alert;
  box.append(el('div', { text: message }));
  box.hidden = false;
}

function clearFormError(box) { box.hidden = true; box.textContent = ''; }

/* Applies an ApiError to a form.

   `fields` maps a server field name to its { input, error } elements. Anything
   the server flagged that has no matching input still has to be shown, so it
   falls through to the form-level alert rather than being silently dropped. */
function applyApiError(err, fields, formErrorBox) {
  if (err.fieldErrors) {
    const unmapped = [];
    for (const [name, message] of Object.entries(err.fieldErrors)) {
      const target = fields[name];
      if (target) setFieldError(target.input, target.error, message);
      else unmapped.push(name + ': ' + message);
    }
    showFormError(formErrorBox, unmapped.length
      ? err.message + ' — ' + unmapped.join('; ')
      : err.message);
    return;
  }
  showFormError(formErrorBox, err.message);
}

Object.assign(window, { setFieldError, showFormError, clearFormError, applyApiError });
