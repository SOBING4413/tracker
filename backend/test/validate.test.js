'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const v = require('../src/services/validate');
const { RateLimiter } = require('../src/services/rateLimit');
const { MemoryStore } = require('../src/services/store');

test('login/register reject non-string input instead of throwing (used to crash the server)', () => {
  for (const bad of [1, {}, [], ['a@b.co'], null, true]) {
    assert.ok(v.parseLogin({ email: bad, password: 'x' }).error);
    assert.ok(v.parseLogin({ email: 'a@b.co', password: bad }).error);
    assert.ok(v.parseRegister({ email: bad, password: 'password1', displayName: 'A' }).error);
    assert.ok(v.parseRegister({ email: 'a@b.co', password: bad, displayName: 'A' }).error);
    assert.ok(v.parseRegister({ email: 'a@b.co', password: 'password1', displayName: bad }).error);
  }
  assert.ok(v.parseLogin(undefined).error);
  assert.ok(v.parseRegister(null).error);
});

test('valid register/login input is accepted and normalised', () => {
  const r = v.parseRegister({ email: 'a@b.co', password: 'password1', displayName: '  Budi  ' });
  assert.deepEqual(r.value, { email: 'a@b.co', password: 'password1', displayName: 'Budi' });
  assert.ok(v.parseRegister({ email: 'a@b.co', password: 'short', displayName: 'A' }).error);
  assert.ok(v.parseLogin({ email: 'a@b.co', password: 'x' }).value);
});

test('group name and invite code parsing', () => {
  assert.equal(v.parseGroupName({ name: ' Touring ' }).value, 'Touring');
  assert.ok(v.parseGroupName({ name: {} }).error);
  assert.ok(v.parseGroupName({}).error);
  assert.equal(v.parseCode({ code: ' abc123 ' }).value, 'abc123');
  assert.ok(v.parseCode({ code: 5 }).error);
});

test('rate limiter blocks after max hits, recovers after the window, sweep frees memory', () => {
  const t = { now: 0 }; const l = new RateLimiter(3, 1000, () => t.now);
  assert.equal(l.hit('ip'), false); assert.equal(l.hit('ip'), false); assert.equal(l.hit('ip'), false);
  assert.equal(l.hit('ip'), true);
  assert.equal(l.hit('other'), false);            // keys are independent
  t.now = 1500; assert.equal(l.hit('ip'), false);  // window passed
  t.now = 5000; l.sweep(); assert.equal(l.hits.size, 0);
});

test('store: userByEmail never throws on non-strings; invite codes are long; caps hold', () => {
  const s = new MemoryStore();
  assert.equal(s.userByEmail(1), undefined);
  assert.equal(s.userByEmail({}), undefined);
  const u = s.createUser({ email: 'A@B.co', displayName: 'a', passwordHash: 'x' });
  assert.equal(s.userByEmail('a@b.CO').id, u.id);
  const g = s.createGroup('G', u.id);
  assert.equal(g.inviteCode.length, 10);
  assert.equal(s.groupByCode(' ' + g.inviteCode.toLowerCase() + ' ').id, g.id);
  for (let i = 0; i < 2300; i++) s.addAlert({ i });
  assert.ok(s.alerts.length <= 2200);
});

test('store: when the owner leaves or is deleted, ownership moves to a remaining member', () => {
  const s = new MemoryStore();
  const a = s.createUser({ email: 'a@x.id', displayName: 'a', passwordHash: 'x' });
  const b = s.createUser({ email: 'b@x.id', displayName: 'b', passwordHash: 'x' });
  const g = s.createGroup('G', a.id); g.members.add(b.id); g.sweeperId = a.id;
  s.deleteUser(a.id);
  assert.equal(g.ownerId, b.id); assert.equal(g.captainId, b.id); assert.equal(g.sweeperId, null);
  assert.equal(g.active, true);
  s.removeMember(g, b.id);
  assert.equal(g.active, false);   // empty group is closed
});
