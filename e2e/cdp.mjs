/* Minimal CDP driver over Node's built-in WebSocket. */
export async function connect(port) {
  let list;
  for (let i = 0; i < 50; i++) {
    try {
      list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
      if (list.some(t => t.type === 'page')) break;
    } catch (e) { /* not up yet */ }
    await new Promise(r => setTimeout(r, 200));
  }
  const page = list.find(t => t.type === 'page');
  if (!page) throw new Error('no page target');

  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });

  let id = 0;
  const pending = new Map();
  const listeners = [];
  ws.onmessage = ev => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { res, rej } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? rej(new Error(msg.error.message)) : res(msg.result);
    } else if (msg.method) {
      for (const fn of listeners) fn(msg);
    }
  };

  const send = (method, params = {}) => new Promise((res, rej) => {
    const n = ++id;
    pending.set(n, { res, rej });
    ws.send(JSON.stringify({ id: n, method, params }));
  });

  const on = fn => listeners.push(fn);

  await send('Page.enable');
  await send('Runtime.enable');
  await send('Network.enable');
  await send('Log.enable');

  /* Runs an expression in the page and returns its value. */
  const evaluate = async expression => {
    const r = await send('Runtime.evaluate', {
      expression, returnByValue: true, awaitPromise: true, userGesture: true
    });
    if (r.exceptionDetails) {
      throw new Error('page threw: ' + (r.exceptionDetails.exception?.description
        || r.exceptionDetails.text));
    }
    return r.result.value;
  };

  const goto = async url => {
    await send('Page.navigate', { url });
    await waitFor(() => evaluate('document.readyState === "complete"'));
  };

  /* The screens navigate with location.href, so waiting means polling the path. */
  const waitForPath = async path => {
    await waitFor(async () => (await evaluate('location.pathname')) === path, `path ${path}`);
    await waitFor(() => evaluate('document.readyState === "complete"'));
  };

  const key = async (k, code, windowsVirtualKeyCode) => {
    for (const type of ['keyDown', 'keyUp']) {
      await send('Input.dispatchKeyEvent', { type, key: k, code, windowsVirtualKeyCode });
    }
  };

  return { send, on, evaluate, goto, waitForPath, key, close: () => ws.close() };
}

export async function waitFor(fn, label = 'condition', tries = 60) {
  for (let i = 0; i < tries; i++) {
    try { if (await fn()) return; } catch (e) { /* keep polling */ }
    await new Promise(r => setTimeout(r, 100));
  }
  throw new Error('timed out waiting for ' + label);
}
