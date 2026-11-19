'use strict';
const crypto = require('crypto');
const MAX_HISTORY = Number(process.env.MAX_HISTORY_POINTS || 200000);
const MAX_ALERTS = 2000;
const MAX_CHAT = 200;   // chat messages kept per group (in memory)
/** In-memory store (development / tests). Swap for a pg-backed implementation with the same API. */
class MemoryStore {
  constructor() { this.users = new Map(); this.groups = new Map(); this.alerts = []; this.history = []; }
  newId() { return crypto.randomUUID(); }
  userByEmail(email) {
    if (typeof email !== 'string') return undefined;
    const e = email.toLowerCase();
    return [...this.users.values()].find((u) => u.email === e);
  }
  createUser(u) { const user = { id: this.newId(), photoUrl: null, ...u, email: u.email.toLowerCase() }; this.users.set(user.id, user); return user; }
  deleteUser(id) {
    this.users.delete(id);
    for (const g of this.groups.values()) {
      if (g.members.has(id)) this.removeMember(g, id);
      if (g.chat) g.chat = g.chat.filter((m) => m.userId !== id);   // an account deletion also removes that person's chat messages
    }
    this.history = this.history.filter((h) => h.userId !== id);
  }
  /** Remove a member and keep the group consistent: a leaving owner hands ownership to a remaining member. */
  removeMember(g, userId) {
    g.members.delete(userId); g.joined?.delete(userId); g.muted?.delete(userId);
    if (g.sweeperId === userId) g.sweeperId = null;
    if (g.ownerId === userId) g.ownerId = g.members.values().next().value ?? null;
    if (g.captainId === userId) g.captainId = g.ownerId;
    if (g.members.size === 0 && g.active) { g.active = false; g.endedAt = Date.now(); }
  }
  createGroup(name, ownerId) {
    const g = { id: this.newId(), name, ownerId, captainId: ownerId, sweeperId: null, thresholdM: 1100,
      inviteCode: crypto.randomBytes(5).toString('hex').toUpperCase(), // 10 hex chars: not practical to guess
      active: true, members: new Set([ownerId]), createdAt: Date.now(),
      joined: new Map([[ownerId, Date.now()]]),   // userId -> join time (shown in the captain panel)
      muted: new Set(),                           // members the captain has muted (no voice, no chat)
      chat: [] };                                 // recent chat messages, capped at MAX_CHAT
    this.groups.set(g.id, g); return g;
  }
  addMember(g, userId) { g.members.add(userId); if (!g.joined.has(userId)) g.joined.set(userId, Date.now()); }
  addChat(g, msg) {
    g.chat.push(msg);
    if (g.chat.length > MAX_CHAT) g.chat.splice(0, g.chat.length - MAX_CHAT);
  }
  groupByCode(code) { const c = String(code).trim().toUpperCase(); return [...this.groups.values()].find((g) => g.inviteCode === c && g.active); }
  groupsOf(userId) { return [...this.groups.values()].filter((g) => g.members.has(userId)); }
  addHistory(row) {
    this.history.push(row);
    if (this.history.length > MAX_HISTORY * 1.1) this.history.splice(0, this.history.length - MAX_HISTORY); // hard memory cap
  }
  addAlert(a) {
    this.alerts.push(a);
    if (this.alerts.length > MAX_ALERTS * 1.1) this.alerts.splice(0, this.alerts.length - MAX_ALERTS);
  }
  purgeHistory(olderThanMs) { this.history = this.history.filter((h) => h.ts >= olderThanMs); }
}
module.exports = { MemoryStore };
