'use strict';
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const express = require('express');
const cfg = require('../config');
const { ALLOWED_THRESHOLDS_M } = require('../services/alertEngine');
const { RateLimiter } = require('../services/rateLimit');
const v = require('../services/validate');

const pub = (u) => ({ id: u.id, email: u.email, displayName: u.displayName, photoUrl: u.photoUrl });
const groupDto = (g, store) => ({ id: g.id, name: g.name, inviteCode: g.inviteCode, captainId: g.captainId, sweeperId: g.sweeperId,
  thresholdM: g.thresholdM, active: g.active, members: [...g.members].map((id) => store.users.get(id)).filter(Boolean).map(pub) });
// Express 4 does not catch rejected promises from async handlers; without this a single bad request can crash the process.
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

function buildApi(store, hub) {
  const api = express.Router();
  api.use(express.json({ limit: '50kb' }));
  const loginLimit = new RateLimiter(10, 60000), registerLimit = new RateLimiter(20, 600000), joinLimit = new RateLimiter(10, 60000);
  setInterval(() => [loginLimit, registerLimit, joinLimit].forEach((l) => l.sweep()), 300000).unref();
  const tooMany = (res) => res.status(429).json({ error: 'too many attempts' });
  const token = (user) => jwt.sign({ sub: user.id }, cfg.jwtSecret, { expiresIn: cfg.tokenTtl });

  api.post('/auth/register', wrap(async (req, res) => {
    if (registerLimit.hit(req.ip)) return tooMany(res);
    const p = v.parseRegister(req.body); if (p.error) return res.status(400).json({ error: p.error });
    const { email, password, displayName } = p.value;
    const passwordHash = await bcrypt.hash(password, 10);
    // check + create happen with no await in between, so two simultaneous registrations can't both win
    if (store.userByEmail(email)) return res.status(409).json({ error: 'email already registered' });
    const user = store.createUser({ email, displayName, passwordHash });
    res.status(201).json({ token: token(user), user: pub(user) });
  }));
  api.post('/auth/login', wrap(async (req, res) => {
    if (loginLimit.hit(req.ip)) return tooMany(res);
    const p = v.parseLogin(req.body); if (p.error) return res.status(400).json({ error: p.error });
    const user = store.userByEmail(p.value.email);
    if (!user || !(await bcrypt.compare(p.value.password, user.passwordHash))) return res.status(401).json({ error: 'invalid credentials' });
    res.json({ token: token(user), user: pub(user) });
  }));

  api.use((req, res, next) => {
    try { const p = jwt.verify((req.headers.authorization || '').replace(/^Bearer /, ''), cfg.jwtSecret); req.user = store.users.get(p.sub); }
    catch { /* fallthrough */ }
    req.user ? next() : res.status(401).json({ error: 'unauthorized' });
  });
  const member = (req, res) => { const g = store.groups.get(req.params.id); if (!g || !g.members.has(req.user.id)) { res.status(403).json({ error: 'forbidden' }); return null; } return g; };
  const captainOnly = (g, req, res) => { if (g.ownerId !== req.user.id && g.captainId !== req.user.id) { res.status(403).json({ error: 'captain or owner only' }); return false; } return true; };

  api.get('/me', (req, res) => res.json(pub(req.user)));
  api.delete('/me', (req, res) => { store.deleteUser(req.user.id); hub.dropUserEverywhere(req.user.id); res.status(204).end(); });
  api.post('/groups', (req, res) => {
    const p = v.parseGroupName(req.body); if (p.error) return res.status(400).json({ error: p.error });
    res.status(201).json(groupDto(store.createGroup(p.value, req.user.id), store));
  });
  api.get('/groups', (req, res) => res.json(store.groupsOf(req.user.id).map((g) => groupDto(g, store))));
  api.post('/groups/join', (req, res) => {
    if (joinLimit.hit(req.user.id)) return tooMany(res);   // invite codes must not be brute-forceable
    const p = v.parseCode(req.body); if (p.error) return res.status(404).json({ error: p.error });
    const g = store.groupByCode(p.value); if (!g) return res.status(404).json({ error: 'invalid code' });
    g.members.add(req.user.id); res.json(groupDto(g, store));
  });
  api.get('/groups/:id', (req, res) => { const g = member(req, res); if (g) res.json(groupDto(g, store)); });
  api.patch('/groups/:id', (req, res) => {
    const g = member(req, res); if (!g || !captainOnly(g, req, res)) return;
    const { captainId, sweeperId, thresholdM } = req.body || {};
    if (captainId !== undefined) { if (!g.members.has(captainId)) return res.status(400).json({ error: 'not a member' }); g.captainId = captainId; }
    if (sweeperId !== undefined) { if (sweeperId !== null && !g.members.has(sweeperId)) return res.status(400).json({ error: 'not a member' }); g.sweeperId = sweeperId; }
    if (thresholdM !== undefined) { if (!ALLOWED_THRESHOLDS_M.includes(thresholdM)) return res.status(400).json({ error: 'thresholdM must be one of ' + ALLOWED_THRESHOLDS_M }); g.thresholdM = thresholdM; }
    res.json(groupDto(g, store));
  });
  api.delete('/groups/:id/members/:uid', (req, res) => {
    const g = member(req, res); if (!g) return;
    if (req.params.uid !== req.user.id && !captainOnly(g, req, res)) return;
    store.removeMember(g, req.params.uid);
    hub.dropUser(g, req.params.uid);   // a removed rider must stop receiving locations immediately
    res.status(204).end();
  });
  api.post('/groups/:id/end', (req, res) => {
    const g = member(req, res); if (!g || !captainOnly(g, req, res)) return;
    g.active = false; g.endedAt = Date.now(); hub.closeGroup(g);
    res.json(groupDto(g, store));
  });
  api.get('/groups/:id/history', (req, res) => { const g = member(req, res); if (!g) return; res.json({ points: store.history.filter((h) => h.groupId === g.id).slice(-5000), alerts: store.alerts.filter((a) => a.groupId === g.id).slice(-200) }); });

  // error handler: malformed JSON -> 400, anything unexpected -> 500 (never crash, never leak internals)
  api.use((err, req, res, next) => {
    if (res.headersSent) return next(err);
    if (err.type === 'entity.too.large') return res.status(413).json({ error: 'payload too large' });
    if (err.type === 'entity.parse.failed' || err.status === 400) return res.status(400).json({ error: 'invalid json' });
    console.error('api error:', err);
    res.status(500).json({ error: 'internal error' });
  });
  return api;
}
module.exports = { buildApi };
