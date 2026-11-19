'use strict';
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const express = require('express');
const cfg = require('../config');
const { ALLOWED_THRESHOLDS_M } = require('../services/alertEngine');

const pub = (u) => ({ id: u.id, email: u.email, displayName: u.displayName, photoUrl: u.photoUrl });
const groupDto = (g, store) => ({ id: g.id, name: g.name, inviteCode: g.inviteCode, captainId: g.captainId, sweeperId: g.sweeperId,
  thresholdM: g.thresholdM, active: g.active, members: [...g.members].map((id) => pub(store.users.get(id))) });

function buildApi(store, hub) {
  const api = express.Router();
  api.use(express.json({ limit: '50kb' }));
  const attempts = new Map(); // naive login rate limit: ip -> [timestamps]
  const limited = (ip) => { const now = Date.now(); const a = (attempts.get(ip) || []).filter((t) => now - t < 60000); a.push(now); attempts.set(ip, a); return a.length > 10; };

  api.post('/auth/register', async (req, res) => {
    const { email, password, displayName } = req.body || {};
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email || '') || (password || '').length < 8 || !displayName) return res.status(400).json({ error: 'invalid input (password >= 8 chars)' });
    if (store.userByEmail(email)) return res.status(409).json({ error: 'email already registered' });
    const user = store.createUser({ email, displayName: String(displayName).slice(0, 40), passwordHash: await bcrypt.hash(password, 10) });
    res.status(201).json({ token: jwt.sign({ sub: user.id }, cfg.jwtSecret, { expiresIn: cfg.tokenTtl }), user: pub(user) });
  });
  api.post('/auth/login', async (req, res) => {
    if (limited(req.ip)) return res.status(429).json({ error: 'too many attempts' });
    const { email, password } = req.body || {};
    const user = email && store.userByEmail(email);
    if (!user || !(await bcrypt.compare(password || '', user.passwordHash))) return res.status(401).json({ error: 'invalid credentials' });
    res.json({ token: jwt.sign({ sub: user.id }, cfg.jwtSecret, { expiresIn: cfg.tokenTtl }), user: pub(user) });
  });

  api.use((req, res, next) => {
    try { const p = jwt.verify((req.headers.authorization || '').replace(/^Bearer /, ''), cfg.jwtSecret); req.user = store.users.get(p.sub); }
    catch { /* fallthrough */ }
    req.user ? next() : res.status(401).json({ error: 'unauthorized' });
  });
  const member = (req, res) => { const g = store.groups.get(req.params.id); if (!g || !g.members.has(req.user.id)) { res.status(403).json({ error: 'forbidden' }); return null; } return g; };
  const captainOnly = (g, req, res) => { if (g.ownerId !== req.user.id && g.captainId !== req.user.id) { res.status(403).json({ error: 'captain or owner only' }); return false; } return true; };

  api.get('/me', (req, res) => res.json(pub(req.user)));
  api.delete('/me', (req, res) => { store.deleteUser(req.user.id); res.status(204).end(); });
  api.post('/groups', (req, res) => { if (!req.body?.name) return res.status(400).json({ error: 'name required' }); res.status(201).json(groupDto(store.createGroup(String(req.body.name).slice(0, 60), req.user.id), store)); });
  api.get('/groups', (req, res) => res.json(store.groupsOf(req.user.id).map((g) => groupDto(g, store))));
  api.post('/groups/join', (req, res) => { const g = store.groupByCode(req.body?.code || ''); if (!g) return res.status(404).json({ error: 'invalid code' }); g.members.add(req.user.id); res.json(groupDto(g, store)); });
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
    g.members.delete(req.params.uid); if (g.captainId === req.params.uid) g.captainId = g.ownerId; res.status(204).end();
  });
  api.post('/groups/:id/end', (req, res) => { const g = member(req, res); if (!g || !captainOnly(g, req, res)) return; g.active = false; g.endedAt = Date.now(); res.json(groupDto(g, store)); });
  api.get('/groups/:id/history', (req, res) => { const g = member(req, res); if (!g) return; res.json({ points: store.history.filter((h) => h.groupId === g.id).slice(-5000), alerts: store.alerts.filter((a) => a.groupId === g.id).slice(-200) }); });
  return api;
}
module.exports = { buildApi };
