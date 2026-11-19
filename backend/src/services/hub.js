'use strict';
const { AlertEngine } = require('./alertEngine');

/**
 * Realtime hub: per-group presence, latest positions, alert engine, PTT floor control, SOS.
 * Transport-agnostic: clients are objects with send(obj). Only authorized group members get data.
 */
class Hub {
  constructor(store, clock = Date.now) {
    this.store = store; this.clock = clock;
    this.rooms = new Map(); // groupId -> {clients:Map(userId->client), pos:Map, engine, floor:{userId,since}|null, sharing:Set}
  }
  room(g) {
    if (!this.rooms.has(g.id)) this.rooms.set(g.id, { clients: new Map(), pos: new Map(), sharing: new Set(),
      engine: new AlertEngine({ thresholdM: g.thresholdM }), floor: null });
    return this.rooms.get(g.id);
  }
  broadcast(r, msg, exceptId) { for (const [id, c] of r.clients) if (id !== exceptId) c.send(msg); }

  join(group, user, client) {
    if (!group.members.has(user.id)) throw new Error('forbidden');
    const r = this.room(group); r.clients.set(user.id, client);
    this.broadcast(r, { t: 'presence', userId: user.id, online: true }, user.id);
    client.send({ t: 'snapshot', positions: [...r.pos.values()], floor: r.floor });
  }
  leave(group, userId) {
    const r = this.rooms.get(group.id); if (!r) return;
    r.clients.delete(userId);
    if (r.floor?.userId === userId) { r.floor = null; this.broadcast(r, { t: 'floor', userId: null }); }
    this.broadcast(r, { t: 'presence', userId, online: false });
  }
  setSharing(group, userId, on) { const r = this.room(group); on ? r.sharing.delete(userId) : r.sharing.add(userId); if (!on) r.pos.delete(userId); }

  location(group, user, fix) {
    const lat = Number(fix.lat), lng = Number(fix.lng);
    if (!(Math.abs(lat) <= 90 && Math.abs(lng) <= 180)) throw new Error('bad coordinates');
    const r = this.room(group); if (r.sharing.has(user.id)) return [];
    const now = this.clock();
    const pos = { userId: user.id, name: user.displayName, photoUrl: user.photoUrl, lat, lng,
      speed: fix.speed ?? null, heading: fix.heading ?? null, ts: now, isCaptain: group.captainId === user.id };
    r.pos.set(user.id, pos);
    this.store.addHistory({ groupId: group.id, userId: user.id, lat, lng, ts: now });
    this.broadcast(r, { t: 'location', ...pos });
    return this.runAlerts(group, r, now);
  }
  runAlerts(group, r, now) {
    r.engine.thresholdM = group.thresholdM;
    const members = [...r.pos.values()].map((p) => ({ ...p, isCaptain: group.captainId === p.userId }));
    const events = r.engine.evaluate(members, now);
    for (const ev of events) {
      this.store.alerts.push({ groupId: group.id, ...ev, ts: now });
      const msg = { t: 'alert', ...ev, thresholdM: group.thresholdM };
      if (ev.type === 'rejoined') { this.broadcast(r, msg); continue; }
      // straggler + Road Captain always; other members get it too (spec: "if needed")
      this.broadcast(r, msg);
    }
    return events;
  }
  ackAlert(group, userId, targetId) { this.room(group).engine.acknowledge(targetId); this.broadcast(this.room(group), { t: 'ack', by: userId, userId: targetId }); }

  // Push-to-talk floor control: one speaker at a time; captain can pre-empt.
  pttRequest(group, userId) {
    const r = this.room(group); const now = this.clock();
    if (r.floor && r.floor.userId !== userId && !(group.captainId === userId)) return { granted: false, holder: r.floor.userId };
    r.floor = { userId, since: now };
    this.broadcast(r, { t: 'floor', userId });
    return { granted: true };
  }
  pttRelease(group, userId) {
    const r = this.room(group);
    if (r.floor?.userId === userId) { r.floor = null; this.broadcast(r, { t: 'floor', userId: null }); }
  }
  /** Relay WebRTC signaling (offer/answer/ice) only between members of the same group. */
  signal(group, from, to, payload) {
    const r = this.room(group); const c = r.clients.get(to);
    if (c && group.members.has(to)) c.send({ t: 'signal', from, payload });
  }
  /** Relay a raw audio frame from the current floor holder to all other online members. */
  audio(group, userId, frame) {
    const r = this.room(group);
    if (r.floor?.userId !== userId) return false; // only the speaker may transmit
    for (const [id, c] of r.clients) if (id !== userId && c.sendBinary) c.sendBinary(frame);
    return true;
  }
  sos(group, user, message) {
    const r = this.room(group); const p = r.pos.get(user.id);
    const ev = { t: 'sos', userId: user.id, name: user.displayName, message: String(message || 'SOS').slice(0, 280),
      lat: p?.lat ?? null, lng: p?.lng ?? null, ts: this.clock(), captainId: group.captainId };
    this.store.alerts.push({ groupId: group.id, type: 'sos', userId: user.id, ts: ev.ts });
    this.broadcast(r, ev); return ev;
  }
}
module.exports = { Hub };
