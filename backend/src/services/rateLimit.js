'use strict';
/** Sliding-window limiter: hit(key) returns true when `key` exceeded `max` hits within `windowMs`. */
class RateLimiter {
  constructor(max, windowMs, clock = Date.now) { this.max = max; this.windowMs = windowMs; this.clock = clock; this.hits = new Map(); }
  hit(key) {
    const now = this.clock();
    const a = (this.hits.get(key) || []).filter((t) => now - t < this.windowMs);
    a.push(now); this.hits.set(key, a);
    return a.length > this.max;
  }
  /** Drop keys with no recent hits so the map can't grow forever. */
  sweep() {
    const now = this.clock();
    for (const [k, a] of this.hits) if (!a.some((t) => now - t < this.windowMs)) this.hits.delete(k);
  }
}
module.exports = { RateLimiter };
