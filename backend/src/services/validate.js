'use strict';
/** Input validation for the REST API. Pure functions, so they can be unit-tested without express. */
const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Returns the value only if it is a string no longer than max, otherwise null (never throws). */
function str(v, max = 256) { return typeof v === 'string' && v.length <= max ? v : null; }

function parseRegister(body) {
  const email = str(body?.email, 254), password = str(body?.password, 128), name = str(body?.displayName, 200)?.trim();
  if (!email || !EMAIL_RE.test(email) || !password || password.length < 8 || !name) return { error: 'invalid input (password >= 8 chars)' };
  return { value: { email, password, displayName: name.slice(0, 40) } };
}
function parseLogin(body) {
  const email = str(body?.email, 254), password = str(body?.password, 128);
  if (!email || !password) return { error: 'invalid input' };
  return { value: { email, password } };
}
function parseGroupName(body) {
  const name = str(body?.name, 200)?.trim();
  return name ? { value: name.slice(0, 60) } : { error: 'name required' };
}
function parseCode(body) {
  const code = str(body?.code, 32)?.trim();
  return code ? { value: code } : { error: 'invalid code' };
}
module.exports = { str, parseRegister, parseLogin, parseGroupName, parseCode };
