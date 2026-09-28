(() => {
  const messages = document.getElementById('messages');
  const form = document.getElementById('message-form');
  const input = document.getElementById('question');
  const sendButton = document.getElementById('send-button');
  const notice = document.getElementById('notice');
  const status = document.getElementById('connection-status');
  const ticketPanel = document.getElementById('ticket-panel');
  const handoffButton = document.getElementById('handoff-button');
  const userToken = sessionStorage.getItem('token');
  function accountScope(token) {
    if (!token) return 'guest';
    try {
      const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const userId = JSON.parse(atob(part)).user;
      if (/^\d+$/.test(String(userId))) return `account:${userId}`;
    } catch (_) { /* 无法解码的令牌仍由服务端拒绝。 */ }
    return `unknown-token:${token.slice(-20)}`;
  }
  const scope = accountScope(userToken);
  const storageKey = `customer-service:${scope}`;
  let session = JSON.parse(sessionStorage.getItem(storageKey) || 'null');
  let controller = null;
  let stopped = false;
  let pending = null;
  let handoffPending = null;
  const seenRunIds = new Set();
  const answerRows = new Map();
  const sourceByRun = new Map();

  const headers = (json = false) => {
    const result = {};
    if (userToken) result.Authorization = userToken;
    if (session && session.guestKey) result['X-Guest-Key'] = session.guestKey;
    if (json) result['Content-Type'] = 'application/json';
    return result;
  };

  function save() { sessionStorage.setItem(storageKey, JSON.stringify(session)); }

  function message(role, text, runId) {
    if (runId && seenRunIds.has(runId)) return answerRows.get(runId);
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
    if (runId) {
      answerRows.set(runId, row);
      const sources = sourceByRun.get(runId);
      if (sources) renderSources(runId, sources);
    }
    messages.scrollTop = messages.scrollHeight;
    return row;
  }

  function renderSources(runId, sources) {
    if (!Array.isArray(sources) || !sources.length) return;
    sourceByRun.set(runId, sources);
    const row = answerRows.get(runId);
    if (!row) return;
    const previous = row.querySelector('.source-list');
    if (previous) previous.remove();
    const list = document.createElement('div');
    list.className = 'source-list';
    for (const source of sources) {
      const card = document.createElement('div');
      card.className = 'source-card';
      const title = document.createElement('strong');
      title.textContent = source.title || '已发布规则';
      const meta = document.createElement('span');
      meta.textContent = `政策 ${source.policyId} · 第 ${source.version} 版 · 生效于 ${source.effectiveFrom}`;
      card.append(title, meta);
      list.append(card);
    }
    row.append(list);
  }

  function showTicket(ticket) {
    if (!ticket || !ticket.ticketId || !ticket.status) return;
    ticketPanel.replaceChildren();
    const title = document.createElement('strong');
    title.textContent = ticket.status === 'QUEUED' ? '人工工单已排队' : '人工工单状态';
    const detail = document.createElement('span');
    const statusText = { QUEUED: '排队中', IN_PROGRESS: '处理中', RESOLVED: '已解决' }[ticket.status] || '处理中';
    detail.textContent = `编号 ${ticket.ticketId} · ${statusText}。人工客服会在后续处理。`;
    ticketPanel.append(title, detail);
    ticketPanel.hidden = false;
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
    seenRunIds.clear();
    answerRows.clear();
    sourceByRun.clear();
    for (const item of (history.messages || []).slice().reverse())
      message(item.role, item.content, item.role === 'assistant' ? item.runId : null);
    if (!history.messages || history.messages.length === 0) message('assistant', '你好！可以问我在售商品、你的订单和已有物流信息。');
    let lastError = null;
    for (const event of (history.events || []).slice().reverse()) {
      try {
        if (event.type === 'sources') renderSources(event.runId, JSON.parse(event.data));
        if (event.type === 'ticket') showTicket(JSON.parse(event.data));
        if (event.type === 'error') lastError = event.data;
        if (event.type === 'completed') lastError = null;
      } catch (_) { /* 忽略单条损坏的历史事件，继续恢复其他消息。 */ }
      session.lastEventId = Math.max(session.lastEventId || 0, Number(event.id));
    }
    if (lastError) showNotice(lastError);
    save();
    try { showTicket(await request(`/conversations/${encodeURIComponent(session.conversationId)}/ticket`)); }
    catch (_) { /* 没有工单是正常状态。 */ }
  }

  function onEvent(eventId, event) {
    if (!eventId || eventId <= (session.lastEventId || 0)) return;
    session.lastEventId = eventId;
    save();
    if (event.type === 'sources') renderSources(event.runId, JSON.parse(event.data));
    if (event.type === 'ticket') showTicket(JSON.parse(event.data));
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
    if (!userToken && /(订单|物流|快递单号)/.test(text)) {
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

  handoffButton.addEventListener('click', async () => {
    if (!session) { showNotice('会话尚未连接，请稍后重试。'); return; }
    const reason = input.value.trim() || '希望人工客服协助处理问题';
    if (!handoffPending || handoffPending.reason !== reason)
      handoffPending = { reason, idempotencyKey: crypto.randomUUID() };
    handoffButton.disabled = true;
    notice.hidden = true;
    try {
      const ticket = await request(`/conversations/${encodeURIComponent(session.conversationId)}/handoff`,
        { method: 'POST', body: JSON.stringify(handoffPending) });
      if (!ticket.ticketId || ticket.status !== 'QUEUED') throw new Error('没有工单编号');
      showTicket(ticket);
      handoffPending = null;
    } catch (_) {
      showNotice('人工工单未创建成功，请点击“转人工”重试。');
    } finally {
      handoffButton.disabled = false;
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
