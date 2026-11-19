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

test('stale socket closing after a reconnect does not evict the new connection', () => {
  const s = setup(); const got = []; let closedOld = false;
  const oldC = s.hub.room(s.g).clients.get(s.budi.id); oldC.close = () => { closedOld = true; };
  const newC = { send: (m) => got.push(m) };
  s.hub.join(s.g, s.budi, newC);
  assert.equal(closedOld, true);
  s.hub.leave(s.g, s.budi.id, oldC);                       // late close event of the old socket
  assert.equal(s.hub.room(s.g).clients.get(s.budi.id), newC);
  assert.ok(!s.inbox.cap.some((m) => m.t === 'presence' && m.online === false));
  s.hub.leave(s.g, s.budi.id, newC);                       // real disconnect
  assert.ok(s.inbox.cap.some((m) => m.t === 'presence' && m.online === false));
});

test('kicked member is disconnected at once and stops receiving locations', () => {
  const s = setup(); let closedWith = null;
  const c = s.hub.room(s.g).clients.get(s.budi.id); c.close = (code) => { closedWith = code; };
  s.g.members.delete(s.budi.id);
  s.hub.dropUser(s.g, s.budi.id);
  assert.equal(closedWith, 4003);
  s.inbox.budi.length = 0;
  s.hub.location(s.g, s.cap, { lat: 1, lng: 1 });
  assert.equal(s.inbox.budi.length, 0);
  assert.ok(s.inbox.cap.some((m) => m.t === 'presence' && m.userId === s.budi.id && m.online === false));
  // the socket's own late close event must not produce a second "offline" broadcast
  const before = s.inbox.cap.filter((m) => m.t === 'presence' && m.online === false).length;
  s.hub.leave(s.g, s.budi.id, c);
  assert.equal(s.inbox.cap.filter((m) => m.t === 'presence' && m.online === false).length, before);
});

test('ending a group disconnects everybody and nobody can join it again', () => {
  const s = setup(); const closed = [];
  for (const id of [s.cap.id, s.budi.id]) s.hub.room(s.g).clients.get(id).close = (code) => closed.push(code);
  s.g.active = false; s.hub.closeGroup(s.g);
  assert.deepEqual(closed, [4003, 4003]);
  assert.throws(() => s.hub.join(s.g, s.budi, { send() {} }), /forbidden/);
});

test('delete account drops the user from every room', () => {
  const s = setup(); let closed = false;
  s.hub.room(s.g).clients.get(s.budi.id).close = () => { closed = true; };
  s.hub.dropUserEverywhere(s.budi.id);
  assert.equal(closed, true);
  assert.equal(s.hub.room(s.g).clients.has(s.budi.id), false);
});

test('join snapshot omits riders whose last fix is stale', () => {
  const s = setup();
  s.hub.location(s.g, s.budi, { lat: 1, lng: 1 });
  s.t.now += 5 * 60000;                                    // 5 minutes later, budi went silent
  s.hub.location(s.g, s.cap, { lat: 1, lng: 1.0001 });
  const late = [];
  const rider = s.store.createUser({ email: 'r@x.id', displayName: 'r', passwordHash: 'x' }); s.g.members.add(rider.id);
  s.hub.join(s.g, rider, { send: (m) => late.push(m) });
  const snap = late.find((m) => m.t === 'snapshot');
  assert.deepEqual(snap.positions.map((p) => p.userId), [s.cap.id]);
});

test('location history is thinned out but keeps real movement', () => {
  const s = setup();
  for (let i = 0; i < 10; i++) { s.t.now += 5000; s.hub.location(s.g, s.budi, { lat: -6.2, lng: 106.8 }); }   // standing still for 50 s
  assert.ok(s.store.history.length <= 3, 'stationary rider: ' + s.store.history.length);
  const n = s.store.history.length;
  s.t.now += 5000; s.hub.location(s.g, s.budi, { lat: -6.2, lng: 106.8 + 0.001 });                            // moved ~110 m
  assert.equal(s.store.history.length, n + 1);
});

test('inactive group cannot be joined, a missing group is rejected without crashing', () => {
  const s = setup(); s.g.active = false;
  assert.throws(() => s.hub.join(s.g, s.cap, { send() {} }), /forbidden/);
  assert.throws(() => s.hub.join(undefined, s.cap, { send() {} }), /forbidden/);
});

// ---- chat, captain mute, captain panel data ----
test('chat: message reaches everyone online (sender included) and is kept for the snapshot', () => {
  const s = setup();
  const msg = s.hub.chat(s.g, s.budi, '  Istirahat di SPBU depan  ');
  assert.equal(msg.text, 'Istirahat di SPBU depan');
  for (const who of ['cap', 'budi']) assert.ok(s.inbox[who].some((m) => m.t === 'chat' && m.id === msg.id));
  const late = []; const eve = s.store.createUser({ email: 'late@x.id', displayName: 'late', passwordHash: 'x' });
  s.store.addMember(s.g, eve.id);
  s.hub.join(s.g, eve, { send: (m) => late.push(m) });
  assert.equal(late.find((m) => m.t === 'snapshot').chat[0].text, 'Istirahat di SPBU depan');
});

test('chat: empty, over-long, rate-limited; announce flag only honoured for the captain', () => {
  const s = setup();
  assert.throws(() => s.hub.chat(s.g, s.budi, '   '), /empty/);
  assert.equal(s.hub.chat(s.g, s.budi, 'x'.repeat(900)).text.length, 500);
  for (let i = 0; i < 4; i++) s.hub.chat(s.g, s.budi, 'hi ' + i);
  assert.throws(() => s.hub.chat(s.g, s.budi, 'terlalu cepat'), /too many/);
  s.t.now += 6000;                                   // window passed
  assert.equal(s.hub.chat(s.g, s.budi, 'lagi', true).announce, false);
  assert.equal(s.hub.chat(s.g, s.cap, 'Semua kumpul', true).announce, true);
});

test('captain mute: blocks chat, PTT and audio; a muted speaker loses the floor; unmute restores', () => {
  const s = setup();
  s.hub.pttRequest(s.g, s.budi.id);
  s.hub.setMuted(s.g, s.budi.id, true);
  assert.equal(s.hub.audio(s.g, s.budi.id, Buffer.alloc(4)), false);
  assert.equal(s.hub.pttRequest(s.g, s.budi.id).granted, false);
  assert.throws(() => s.hub.chat(s.g, s.budi, 'halo'), /muted/);
  assert.ok(s.inbox.cap.some((m) => m.t === 'muted' && m.userId === s.budi.id && m.muted === true));
  assert.ok(s.inbox.cap.some((m) => m.t === 'floor' && m.userId === null));
  s.hub.setMuted(s.g, s.budi.id, false);
  assert.equal(s.hub.pttRequest(s.g, s.budi.id).granted, true);
});

test('adminView: roles, presence, speed, distance to captain; paused sharing exposes no position', () => {
  const s = setup();
  s.hub.location(s.g, s.cap, { lat: -6.2, lng: 106.8 });
  s.hub.location(s.g, s.budi, { lat: -6.2 - 1 / 111.19, lng: 106.8, speed: 10 });
  s.t.now += 5000;
  let v = s.hub.adminView(s.g);
  const b = v.find((m) => m.id === s.budi.id), c = v.find((m) => m.id === s.cap.id);
  assert.equal(c.role, 'captain'); assert.equal(c.distanceToCaptainM, null);
  assert.equal(b.role, 'member'); assert.equal(b.online, true); assert.equal(b.speedKmh, 36);
  assert.ok(Math.abs(b.distanceToCaptainM - 1000) < 30); assert.equal(b.lastSeenMs, 5000);
  s.hub.setSharing(s.g, s.budi.id, false);
  v = s.hub.adminView(s.g); const b2 = v.find((m) => m.id === s.budi.id);
  assert.equal(b2.sharing, false); assert.equal(b2.lat, null);
});

test('store: removing a member clears mute/join info; deleting an account removes their chat messages', () => {
  const s = setup();
  s.store.addMember(s.g, s.budi.id); s.hub.setMuted(s.g, s.budi.id, true); s.hub.chat(s.g, s.cap, 'a');
  s.hub.setMuted(s.g, s.budi.id, false); s.hub.chat(s.g, s.budi, 'b');
  s.store.deleteUser(s.budi.id);
  assert.deepEqual(s.g.chat.map((m) => m.text), ['a']);
  assert.ok(!s.g.joined.has(s.budi.id) && !s.g.muted.has(s.budi.id));
});

test('notifyGroupChanged: online members are told to re-fetch the group', () => {
  const s = setup();
  s.hub.notifyGroupChanged(s.g);
  assert.ok(s.inbox.cap.some((m) => m.t === 'group') && s.inbox.budi.some((m) => m.t === 'group'));
});
