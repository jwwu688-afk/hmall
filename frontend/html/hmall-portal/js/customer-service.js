(() => {
  const messages = document.getElementById('messages');
  const form = document.getElementById('message-form');
  const input = document.getElementById('question');
  const sendButton = document.getElementById('send-button');
  const notice = document.getElementById('notice');
  const status = document.getElementById('connection-status');
  const userToken = sessionStorage.getItem('token');
  const scope = userToken ? `account:${userToken.slice(-20)}` : 'guest';
  const storageKey = `customer-service:${scope}`;
  let session = JSON.parse(sessionStorage.getItem(storageKey) || 'null');
  let controller = null;
  let stopped = false;
  let pending = null;
  const seenRunIds = new Set();

  const headers = (json = false) => {
    const result = {};
    if (userToken) result.Authorization = userToken;
    if (session && session.guestKey) result['X-Guest-Key'] = session.guestKey;
    if (json) result['Content-Type'] = 'application/json';
    return result;
  };

  function save() { sessionStorage.setItem(storageKey, JSON.stringify(session)); }

  function message(role, text, runId) {
    if (runId && seenRunIds.has(runId)) return;
    if (runId) seenRunIds.add(runId);
    const row = document.createElement('div');
    row.className = `message ${role === 'user' ? 'user' : 'assistant'}`;
    const label = document.createElement('span');
    label.className = 'message-label';
    label.textContent = role === 'user' ? '我' : '智能客服';
    const body = document.createElement('div');
    body.className = 'message-body';
    body.textContent = text;
    row.append(label, body);
    messages.appendChild(row);
    messages.scrollTop = messages.scrollHeight;
  }

  function showNotice(text, login = false) {
    notice.replaceChildren();
    notice.append(document.createTextNode(text));
    if (login) {
      const link = document.createElement('a');
      link.href = '/login.html';
      link.textContent = ' 前往登录';
      notice.append(link);
    }
    notice.hidden = false;
  }

  async function request(path, options = {}) {
    const response = await fetch(`/api/customer-service${path}`, {
      ...options, headers: { ...headers(!!options.body), ...(options.headers || {}) }
    });
    if (!response.ok) throw new Error(`请求失败（${response.status}）`);
    return response.json();
  }

  async function ensureSession() {
    if (!session || !session.conversationId) {
      session = await request('/conversations', { method: 'POST' });
      session.lastEventId = 0;
      save();
    }
    const history = await request(`/conversations/${encodeURIComponent(session.conversationId)}`);
    messages.replaceChildren();
    for (const item of history.messages || []) message(item.role, item.content, item.role === 'assistant' ? item.runId : null);
    if (!history.messages || history.messages.length === 0) message('assistant', '你好！可以问我在售商品、你的订单和已有物流信息。');
  }

  function onEvent(eventId, event) {
    if (!eventId || eventId <= (session.lastEventId || 0)) return;
    session.lastEventId = eventId;
    save();
    if (event.type === 'completed') message('assistant', event.data, event.runId);
    if (event.type === 'error') showNotice(event.data || '客服服务暂时不可用，请稍后重试。');
  }

  async function connect() {
    if (stopped || !session) return;
    controller = new AbortController();
    try {
      const url = `/api/customer-service/conversations/${encodeURIComponent(session.conversationId)}/events?after=${session.lastEventId || 0}`;
      const response = await fetch(url, { headers: headers(), signal: controller.signal });
      if (!response.ok || !response.body) throw new Error('事件连接失败');
      status.textContent = '已连接';
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      while (!stopped) {
        const chunk = await reader.read();
        if (chunk.done) break;
        buffer = (buffer + decoder.decode(chunk.value, { stream: true })).replace(/\r\n/g, '\n');
        let end;
        while ((end = buffer.indexOf('\n\n')) !== -1) {
          const frame = buffer.slice(0, end);
          buffer = buffer.slice(end + 2);
          const idLine = frame.split('\n').find(line => line.startsWith('id:'));
          const dataLine = frame.split('\n').find(line => line.startsWith('data:'));
          if (idLine && dataLine) {
            try { onEvent(Number(idLine.slice(3).trim()), JSON.parse(dataLine.slice(5).trim())); }
            catch (_) { showNotice('收到无法解析的客服事件，请刷新页面。'); }
          }
        }
      }
    } catch (error) {
      if (!stopped && error.name !== 'AbortError') status.textContent = '连接中断，正在重连';
    }
    if (!stopped) window.setTimeout(connect, 1200);
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const text = input.value.trim();
    if (!text || !session) return;
    if (!userToken && /(订单|物流|发货|快递|退款|退货)/.test(text)) {
      showNotice('查询订单或物流信息需要先登录。', true);
      return;
    }
    if (!pending || pending.message !== text) pending = { message: text, idempotencyKey: crypto.randomUUID() };
    sendButton.disabled = true;
    notice.hidden = true;
    try {
      await request(`/conversations/${encodeURIComponent(session.conversationId)}/messages`, {
        method: 'POST', body: JSON.stringify(pending)
      });
      message('user', text);
      input.value = '';
      pending = null;
    } catch (_) {
      showNotice('消息暂时没有发送成功，请重试。');
    } finally {
      sendButton.disabled = false;
    }
  });

  document.querySelectorAll('[data-question]').forEach(button => {
    button.addEventListener('click', () => { input.value = button.dataset.question; input.focus(); });
  });
  const prefillOrder = new URLSearchParams(location.search).get('orderId');
  if (prefillOrder && /^\d{1,20}$/.test(prefillOrder)) input.value = `查询订单 ${prefillOrder} 的物流`;
  window.addEventListener('pagehide', () => { stopped = true; if (controller) controller.abort(); });
  ensureSession().then(connect).catch(() => { status.textContent = '连接失败'; showNotice('客服暂时无法连接，请稍后刷新页面。'); });
})();
