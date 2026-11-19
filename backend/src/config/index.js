'use strict';
const crypto = require('crypto');
let secret = process.env.JWT_SECRET;
if (!secret && process.env.NODE_ENV === 'production') throw new Error('JWT_SECRET is required in production');
if (!secret && process.env.RENDER) {
  // Hosted (Render sets RENDER=true) but no secret configured: never fall back to the public dev secret.
  secret = crypto.randomBytes(32).toString('hex');
  console.warn('WARNING: JWT_SECRET is not set; using a random one (logins reset on every restart). Set JWT_SECRET in the Render Environment tab.');
}
module.exports = {
  port: Number(process.env.PORT || 8080),
  jwtSecret: secret || 'dev-only-insecure-secret',
  tokenTtl: '7d',
  historyRetentionDays: Number(process.env.HISTORY_RETENTION_DAYS || 7),
};
