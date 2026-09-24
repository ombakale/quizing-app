/* Navbar — the only persistent chrome.

   Replaces the previous frontend's `Logged in: admin (ADMIN)` text node with a
   username plus a role badge, so the raw wire enum never surfaces. Depends on
   app.js for el(), ICON, session and roleLabel. */

function Navbar({ current, onLogout }) {
  const brand = el('a', {
    class: 'navbar-brand',
    href: current ? 'dashboard.html' : 'index.html',
    html: ICON.brand + '<span>Quiz Application</span>'
  });

  const right = el('div', { class: 'navbar-identity' });
  if (current) {
    right.append(
      el('span', { class: 'navbar-username', text: current.username }),
      el('span', { class: 'badge badge-on-accent', text: roleLabel(current.role) }),
      el('button', { class: 'btn btn-on-accent btn-sm', type: 'button',
                     onclick: onLogout, text: 'Logout' })
    );
  }

  return el('nav', { class: 'navbar' },
    el('div', { class: 'navbar-inner' }, [brand, right]));
}

/* Mounts the navbar and wires logout to drop the token and the in-flight attempt. */
function renderNavbar(mount) {
  mount.append(Navbar({
    current: session.get(),
    onLogout: () => {
      handoff.clear();
      session.clear();
      location.href = 'index.html';
    }
  }));
}

Object.assign(window, { Navbar, renderNavbar });
