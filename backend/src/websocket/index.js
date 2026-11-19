'use strict';
const { WebSocketServer } = require('ws');
const jwt = require('jsonwebtoken');
const cfg = require('../config');

/**
 * Connect: wss://host/ws?token=JWT&group=GROUP_ID  (use TLS terminator in production)
 * Close codes: 4003 = rejected/removed from the group (don't retry fast), 4000 = replaced by a newer connection of the same user.
 */
function attachWebSocket(server, store, hub) {
  const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 16 * 1024 }); // audio frames are <= 8 KB
  wss.on('connection', (ws, req) => {
    ws.on('error', () => {}); // a bad frame must never crash the process (that drops every client at once)
    const q = new URL(req.url, 'http://x').searchParams;
    let user, group, client;
    try {
      user = store.users.get(jwt.verify(q.get('token') || '', cfg.jwtSecret).sub); group = store.groups.get(q.get('group'));
      client = {
        send: (m) => ws.readyState === 1 && ws.send(JSON.stringify(m)),
        sendBinary: (b) => ws.readyState === 1 && ws.send(b, { binary: true }),
        close: (code = 4000, reason = 'replaced') => ws.close(code, reason),
      };
      hub.join(group, user, client);
    } catch { return ws.close(4003, 'forbidden'); }
    ws.isAlive = true; ws.on('pong', () => { ws.isAlive = true; });
    ws.on('message', (raw, isBinary) => {
      // Membership is re-checked on every message: being kicked or the group ending takes effect immediately.
      if (!group.active || !group.members.has(user.id)) return ws.close(4003, 'forbidden');
      if (isBinary) { if (raw.length <= 8192) hub.audio(group, user.id, raw); return; }
      let m; try { m = JSON.parse(raw); } catch { return; }
      if (!m || typeof m !== 'object') return;
      try {
        switch (m.t) {
          case 'location': hub.location(group, user, m); break;
          case 'share': hub.setSharing(group, user.id, !!m.on); break;
          case 'ptt_start': ws.send(JSON.stringify({ t: 'ptt_result', ...hub.pttRequest(group, user.id) })); break;
          case 'ptt_stop': hub.pttRelease(group, user.id); break;
          case 'signal': hub.signal(group, user.id, m.to, m.payload); break;
          case 'ack': hub.ackAlert(group, user.id, m.userId); break;
          case 'chat': hub.chat(group, user, m.text, m.announce); break;
          case 'sos': hub.sos(group, user, m.message); break;
          case 'ping': ws.send('{"t":"pong"}'); break;
        }
      } catch (e) { ws.send(JSON.stringify({ t: 'error', message: e.message })); }
    });
    ws.on('close', () => hub.leave(group, user.id, client));
  });
  const iv = setInterval(() => wss.clients.forEach((c) => { if (!c.isAlive) return c.terminate(); c.isAlive = false; c.ping(); }), 15000);
  wss.on('close', () => clearInterval(iv));
  return wss;
}
module.exports = { attachWebSocket };
