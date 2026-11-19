'use strict';
const { WebSocketServer } = require('ws');
const jwt = require('jsonwebtoken');
const cfg = require('../config');

/** Connect: wss://host/ws?token=JWT&group=GROUP_ID  (use TLS terminator in production) */
function attachWebSocket(server, store, hub) {
  const wss = new WebSocketServer({ server, path: '/ws' });
  wss.on('connection', (ws, req) => {
    const q = new URL(req.url, 'http://x').searchParams;
    let user, group;
    try { user = store.users.get(jwt.verify(q.get('token') || '', cfg.jwtSecret).sub); group = store.groups.get(q.get('group')); hub.join(group, user, { send: (m) => ws.readyState === 1 && ws.send(JSON.stringify(m)), sendBinary: (b) => ws.readyState === 1 && ws.send(b, { binary: true }) }); }
    catch { return ws.close(4003, 'forbidden'); }
    ws.isAlive = true; ws.on('pong', () => { ws.isAlive = true; });
    ws.on('message', (raw, isBinary) => {
      if (isBinary) { if (raw.length <= 8192) hub.audio(group, user.id, raw); return; }
      let m; try { m = JSON.parse(raw); } catch { return; }
      try {
        switch (m.t) {
          case 'location': hub.location(group, user, m); break;
          case 'share': hub.setSharing(group, user.id, !!m.on); break;
          case 'ptt_start': ws.send(JSON.stringify({ t: 'ptt_result', ...hub.pttRequest(group, user.id) })); break;
          case 'ptt_stop': hub.pttRelease(group, user.id); break;
          case 'signal': hub.signal(group, user.id, m.to, m.payload); break;
          case 'ack': hub.ackAlert(group, user.id, m.userId); break;
          case 'sos': hub.sos(group, user, m.message); break;
          case 'ping': ws.send('{"t":"pong"}'); break;
        }
      } catch (e) { ws.send(JSON.stringify({ t: 'error', message: e.message })); }
    });
    ws.on('close', () => hub.leave(group, user.id));
  });
  const iv = setInterval(() => wss.clients.forEach((c) => { if (!c.isAlive) return c.terminate(); c.isAlive = false; c.ping(); }), 15000);
  wss.on('close', () => clearInterval(iv));
  return wss;
}
module.exports = { attachWebSocket };
