'use strict';
const { haversine } = require('./geo');

const ALLOWED_THRESHOLDS_M = [500, 1100, 1500, 2000];

/**
 * Straggler detection.
 *
 * The "main group" is the set of members reachable from the Road Captain via
 * a chain of hops each <= threshold (so a long but evenly spaced convoy is not
 * flagged, but a rider left behind a gap is). If the captain is offline the
 * seed is the largest connected cluster. Members outside the main group are
 * "separated"; their distance is to the nearest main-group member.
 *
 * Hysteresis: a separated member only rejoins when within clearRatio*threshold.
 * Stale members (no fix within staleMs) are treated as offline and ignored.
 */
class AlertEngine {
  constructor(opts = {}) {
    this.thresholdM = opts.thresholdM ?? 1100;
    this.clearRatio = opts.clearRatio ?? 0.9;
    this.staleMs = opts.staleMs ?? 60000;
    this.repeatMs = opts.repeatMs ?? 60000;
    this.separated = new Map(); // userId -> {since, lastNotified, distanceM, acked}
  }

  setThreshold(m) {
    if (!ALLOWED_THRESHOLDS_M.includes(m)) throw new Error('invalid threshold');
    this.thresholdM = m;
  }

  /**
   * @param members [{userId,name,lat,lng,ts,isCaptain}]
   * @returns events [{type:'separated'|'repeat'|'rejoined', userId, name, distanceM}]
   */
  evaluate(members, now = Date.now()) {
    const active = members.filter((m) => now - m.ts <= this.staleMs);
    const events = [];
    const T = this.thresholdM;
    const clearT = T * this.clearRatio;

    const inMain = new Set();
    const dist = new Map(); // userId -> distance to nearest main member
    if (active.length > 0) {
      const edgeLimit = (m) => (this.separated.has(m.userId) ? clearT : T);
      const grow = (seeds) => {
        const main = new Set(seeds.map((s) => s.userId));
        const queue = [...seeds];
        while (queue.length) {
          const cur = queue.shift();
          for (const o of active) {
            if (main.has(o.userId)) continue;
            if (haversine(cur, o) <= edgeLimit(o)) { main.add(o.userId); queue.push(o); }
          }
        }
        return main;
      };
      const captain = active.find((m) => m.isCaptain);
      let main;
      if (captain) main = grow([captain]);
      else {
        main = new Set();
        for (const m of active) {
          const c = grow([m]);
          if (c.size > main.size) main = c;
        }
      }
      main.forEach((id) => inMain.add(id));
      const mainMembers = active.filter((m) => inMain.has(m.userId));
      for (const m of active) {
        if (inMain.has(m.userId)) continue;
        let best = Infinity;
        for (const o of mainMembers) best = Math.min(best, haversine(m, o));
        dist.set(m.userId, best);
      }
    }

    for (const m of active) {
      const st = this.separated.get(m.userId);
      if (inMain.has(m.userId)) {
        if (st) {
          this.separated.delete(m.userId);
          events.push({ type: 'rejoined', userId: m.userId, name: m.name, distanceM: 0 });
        }
        continue;
      }
      const d = Math.round(dist.get(m.userId));
      if (!st) {
        this.separated.set(m.userId, { since: now, lastNotified: now, distanceM: d, acked: false });
        events.push({ type: 'separated', userId: m.userId, name: m.name, distanceM: d });
      } else {
        st.distanceM = d;
        if (!st.acked && now - st.lastNotified >= this.repeatMs) {
          st.lastNotified = now;
          events.push({ type: 'repeat', userId: m.userId, name: m.name, distanceM: d });
        }
      }
    }
    // members that went offline are dropped from the separated set
    for (const id of [...this.separated.keys()]) {
      if (!active.some((m) => m.userId === id)) this.separated.delete(id);
    }
    return events;
  }

  acknowledge(userId) {
    const st = this.separated.get(userId);
    if (st) st.acked = true;
  }
}

module.exports = { AlertEngine, ALLOWED_THRESHOLDS_M };
