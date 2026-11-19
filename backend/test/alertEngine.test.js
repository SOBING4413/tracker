'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { AlertEngine } = require('../src/services/alertEngine');
const { haversine } = require('../src/services/geo');

// 1 degree latitude ~ 111.19 km -> helper: point `km` north of origin
const at = (km) => ({ lat: -6.2 + km / 111.19, lng: 106.8 });
const mem = (id, km, extra = {}) => ({ userId: id, name: id, ...at(km), ts: 1000, ...extra });

test('haversine ~111.19 km per degree latitude', () => {
  const d = haversine({ lat: 0, lng: 0 }, { lat: 1, lng: 0 });
  assert.ok(Math.abs(d - 111195) < 200);
});

test('no alert when group is tight', () => {
  const e = new AlertEngine();
  const ev = e.evaluate([mem('cap', 0, { isCaptain: true }), mem('a', 0.3), mem('b', 0.6)], 1000);
  assert.deepEqual(ev, []);
});

test('rider 1.5 km behind nearest group member triggers alert with distance', () => {
  const e = new AlertEngine();
  const ev = e.evaluate([mem('cap', 1.2, { isCaptain: true }), mem('a', 1.0), mem('budi', -0.5)], 1000);
  assert.equal(ev.length, 1);
  assert.equal(ev[0].type, 'separated');
  assert.equal(ev[0].userId, 'budi');
  assert.ok(Math.abs(ev[0].distanceM - 1500) < 20);
});

test('exactly 1.2 km gap flagged, 1.0 km gap not', () => {
  assert.equal(new AlertEngine().evaluate([mem('cap', 1.2, { isCaptain: true }), mem('b', 0)], 1000).length, 1);
  assert.equal(new AlertEngine().evaluate([mem('cap', 1.0, { isCaptain: true }), mem('b', 0)], 1000).length, 0);
});

test('evenly spaced long convoy (chain) is not flagged', () => {
  const e = new AlertEngine();
  const ms = [mem('cap', 4, { isCaptain: true }), mem('a', 3.0), mem('b', 2.0), mem('c', 1.0), mem('d', 0)];
  assert.deepEqual(e.evaluate(ms, 1000), []);
});

test('hysteresis: no rejoin until within 90% of threshold; no repeat flapping', () => {
  const e = new AlertEngine({ repeatMs: 10 ** 9 });
  e.evaluate([mem('cap', 1.2, { isCaptain: true }), mem('b', 0)], 1000);
  // gap shrinks to 1.05 km: below 1.1 but above 0.99 -> still separated, no new event
  assert.deepEqual(e.evaluate([mem('cap', 1.05, { isCaptain: true }), mem('b', 0)], 2000), []);
  assert.ok(e.separated.has('b'));
  // gap 0.9 km -> rejoined
  const ev = e.evaluate([mem('cap', 0.9, { isCaptain: true }), mem('b', 0)], 3000);
  assert.equal(ev[0].type, 'rejoined');
  // hovering at 1.05 km after rejoin does NOT re-alert (below 1.1 entry)
  assert.deepEqual(e.evaluate([mem('cap', 1.05, { isCaptain: true }), mem('b', 0)], 4000), []);
});

test('repeat notifications respect interval and acknowledgement', () => {
  const e = new AlertEngine({ repeatMs: 5000 });
  const ms = [mem('cap', 2, { isCaptain: true }), mem('b', 0)];
  e.evaluate(ms.map((m) => ({ ...m, ts: 0 })), 0);
  assert.equal(e.evaluate(ms.map((m) => ({ ...m, ts: 3000 })), 3000).length, 0);
  assert.equal(e.evaluate(ms.map((m) => ({ ...m, ts: 6000 })), 6000)[0].type, 'repeat');
  e.acknowledge('b');
  assert.equal(e.evaluate(ms.map((m) => ({ ...m, ts: 20000 })), 20000).length, 0);
});

test('stale (offline) members are ignored, not flagged', () => {
  const e = new AlertEngine({ staleMs: 60000 });
  const ev = e.evaluate([mem('cap', 5, { isCaptain: true, ts: 100000 }), mem('b', 0, { ts: 1000 })], 100000);
  assert.deepEqual(ev, []);
});

test('without captain, largest cluster is the main group', () => {
  const e = new AlertEngine();
  const ev = e.evaluate([mem('a', 0), mem('b', 0.2), mem('c', 0.4), mem('lost', 3)], 1000);
  assert.equal(ev.length, 1);
  assert.equal(ev[0].userId, 'lost');
});

test('threshold options validated', () => {
  const e = new AlertEngine();
  e.setThreshold(500);
  assert.throws(() => e.setThreshold(700));
});
