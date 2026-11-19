'use strict';
const secret = process.env.JWT_SECRET;
if (!secret && process.env.NODE_ENV === 'production') throw new Error('JWT_SECRET is required in production');
module.exports = {
  port: Number(process.env.PORT || 8080),
  jwtSecret: secret || 'dev-only-insecure-secret',
  tokenTtl: '7d',
  historyRetentionDays: Number(process.env.HISTORY_RETENTION_DAYS || 30),
};
