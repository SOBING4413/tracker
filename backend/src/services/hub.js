'use strict';
const { AlertEngine } = require('./alertEngine');
const { haversine } = require('./geo');

// Location history is thinned out so memory stays bounded: keep a point every 30 s, or sooner after moving 10 m.
const HIST_MIN_MS = 30000, HIST_MIN_M = 10;

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
      engine: new AlertEngine({ thresholdM: g.thresholdM }), floor: null, lastHist: new Map() });
    return this.rooms.get(g.id);
  }
  broadcast(r, msg, exceptId) { for (const [id, c] of r.clients) if (id !== exceptId) c.send(msg); }

  join(group, user, client) {
    if (!group || !group.active || !group.members.has(user.id)) throw new Error('forbidden');
    const r = this.room(group);
    const old = r.clients.get(user.id);
    r.clients.set(user.id, client);
    if (old && old !== client) old.close?.();   // same user reconnecting: drop the stale (half-open) socket
    this.broadcast(r, { t: 'presence', userId: user.id, online: true }, user.id);
    const now = this.clock();   // don't present long-gone riders' last positions as if they were live
    client.send({ t: 'snapshot', positions: [...r.pos.values()].filter((p) => now - p.ts <= r.engine.staleMs), floor: r.floor });
  }
  /** `client` = the connection that closed; if the user already reconnected with a newer one, do nothing. */
  leave(group, userId, client) {
    const r = this.rooms.get(group.id); if (!r) return;
    if (client && r.clients.get(userId) !== client) return;
    r.clients.delete(userId);
    if (r.floor?.userId === userId) { r.floor = null; this.broadcast(r, { t: 'floor', userId: null }); }
    this.broadcast(r, { t: 'presence', userId, online: false });
  }
  /** Disconnect a user from a group right now (kicked, left, account deleted). */
  dropUser(group, userId) {
    const r = this.rooms.get(group.id); const c = r?.clients.get(userId); if (!c) return;
    c.close?.(4003, 'removed');
    this.leave(group, userId, c);
  }
  dropUserEverywhere(userId) {
    for (const [gid, r] of this.rooms) if (r.clients.has(userId)) this.dropUser({ id: gid }, userId);
  }
  /** The group ended: disconnect everybody and forget the room. */
  closeGroup(group) {
    const r = this.rooms.get(group.id); if (!r) return;
    for (const id of [...r.clients.keys()]) this.dropUser(group, id);
    this.rooms.delete(group.id);
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
    const last = r.lastHist.get(user.id);
    if (!last || now - last.ts >= HIST_MIN_MS || haversine(last, pos) >= HIST_MIN_M) {
      this.store.addHistory({ groupId: group.id, userId: user.id, lat, lng, ts: now });
      r.lastHist.set(user.id, { ts: now, lat, lng });
    }
    this.broadcast(r, { t: 'location', ...pos });
    return this.runAlerts(group, r, now);
  }
  runAlerts(group, r, now) {
    r.engine.thresholdM = group.thresholdM;
    const members = [...r.pos.values()].map((p) => ({ ...p, isCaptain: group.captainId === p.userId }));
    const events = r.engine.evaluate(members, now);
    for (const ev of events) {
      this.store.addAlert({ groupId: group.id, ...ev, ts: now });
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
    this.store.addAlert({ groupId: group.id, type: 'sos', userId: user.id, ts: ev.ts });
    this.broadcast(r, ev); return ev;
  }
}
module.exports = { Hub };
