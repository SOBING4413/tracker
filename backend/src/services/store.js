'use strict';
const crypto = require('crypto');
/** In-memory store (development / tests). Swap for a pg-backed implementation with the same API. */
class MemoryStore {
  constructor() { this.users = new Map(); this.groups = new Map(); this.alerts = []; this.history = []; }
  newId() { return crypto.randomUUID(); }
  userByEmail(email) { return [...this.users.values()].find((u) => u.email === email.toLowerCase()); }
  createUser(u) { const user = { id: this.newId(), photoUrl: null, ...u, email: u.email.toLowerCase() }; this.users.set(user.id, user); return user; }
  deleteUser(id) {
    this.users.delete(id);
    for (const g of this.groups.values()) { g.members.delete(id); if (g.captainId === id) g.captainId = null; if (g.sweeperId === id) g.sweeperId = null; }
    this.history = this.history.filter((h) => h.userId !== id);
  }
  createGroup(name, ownerId) {
    const g = { id: this.newId(), name, ownerId, captainId: ownerId, sweeperId: null, thresholdM: 1100,
      inviteCode: crypto.randomBytes(3).toString('hex').toUpperCase(), active: true, members: new Set([ownerId]), createdAt: Date.now() };
    this.groups.set(g.id, g); return g;
  }
  groupByCode(code) { return [...this.groups.values()].find((g) => g.inviteCode === String(code).toUpperCase() && g.active); }
  groupsOf(userId) { return [...this.groups.values()].filter((g) => g.members.has(userId)); }
  addHistory(row) { this.history.push(row); }
  purgeHistory(olderThanMs) { this.history = this.history.filter((h) => h.ts >= olderThanMs); }
}
module.exports = { MemoryStore };
