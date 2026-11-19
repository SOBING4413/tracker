'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { MemoryStore } = require('../src/services/store');
const { Hub } = require('../src/services/hub');

function setup() {
  const store = new MemoryStore(); const t = { now: 1000 };
  const hub = new Hub(store, () => t.now);
  const mk = (n) => store.createUser({ email: n + '@x.id', displayName: n, passwordHash: 'x' });
  const cap = mk('cap'), budi = mk('budi'), outsider = mk('eve');
  const g = store.createGroup('Touring', cap.id); g.members.add(budi.id);
  const inbox = { cap: [], budi: [], eve: [] };
  hub.join(g, cap, { send: (m) => inbox.cap.push(m) });
  hub.join(g, budi, { send: (m) => inbox.budi.push(m) });
  return { store, hub, g, cap, budi, outsider, inbox, t };
}

test('non-member cannot join room', () => {
  const s = setup();
  assert.throws(() => s.hub.join(s.g, s.outsider, { send() {} }), /forbidden/);
});

test('straggler alert is broadcast to the group at >1.1 km', () => {
  const s = setup();
  s.hub.location(s.g, s.cap, { lat: -6.2, lng: 106.8 });
  const ev = s.hub.location(s.g, s.budi, { lat: -6.2 - 1.2 / 111.19, lng: 106.8 });
  assert.equal(ev[0].type, 'separated');
  const a = s.inbox.cap.find((m) => m.t === 'alert');
  assert.equal(a.userId, s.budi.id);
  assert.ok(a.distanceM > 1100);
  assert.ok(s.inbox.budi.some((m) => m.t === 'alert'));
});

test('privacy: paused sharing hides position', () => {
  const s = setup();
  s.hub.setSharing(s.g, s.budi.id, false);
  s.hub.location(s.g, s.budi, { lat: 1, lng: 1 });
  assert.ok(!s.inbox.cap.some((m) => m.t === 'location'));
});

test('PTT floor control: one speaker, captain can pre-empt, release frees', () => {
  const s = setup();
  assert.equal(s.hub.pttRequest(s.g, s.budi.id).granted, true);
  assert.equal(s.hub.pttRequest(s.g, s.cap.id).granted, true); // captain pre-empts
  assert.equal(s.hub.pttRequest(s.g, s.budi.id).granted, false);
  s.hub.pttRelease(s.g, s.cap.id);
  assert.equal(s.hub.pttRequest(s.g, s.budi.id).granted, true);
});

test('SOS carries last location and reaches the captain', () => {
  const s = setup();
  s.hub.location(s.g, s.budi, { lat: -6.3, lng: 106.9 });
  const ev = s.hub.sos(s.g, s.budi, 'Ban bocor');
  assert.equal(ev.lat, -6.3);
  assert.ok(s.inbox.cap.some((m) => m.t === 'sos' && m.captainId === s.cap.id));
});

test('invalid coordinates rejected', () => {
  const s = setup();
  assert.throws(() => s.hub.location(s.g, s.budi, { lat: 999, lng: 0 }), /bad coordinates/);
});

test('audio frames relay only from floor holder to others', () => {
  const s = setup(); const got = [];
  s.hub.room(s.g).clients.get(s.cap.id).sendBinary = (b) => got.push(b);
  s.hub.room(s.g).clients.get(s.budi.id).sendBinary = () => {};
  assert.equal(s.hub.audio(s.g, s.budi.id, Buffer.from([1])), false); // no floor yet
  s.hub.pttRequest(s.g, s.budi.id);
  assert.equal(s.hub.audio(s.g, s.budi.id, Buffer.from([1, 2])), true);
  assert.equal(got.length, 1);
});
