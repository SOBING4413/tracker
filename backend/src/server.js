'use strict';
const http = require('http');
const express = require('express');
const cfg = require('./config');
const { MemoryStore } = require('./services/store');
const { Hub } = require('./services/hub');
const { buildApi } = require('./controllers/api');
const { attachWebSocket } = require('./websocket');

// State lives in memory, so a restart loses everything: prefer logging an unexpected error over dying from it.
process.on('unhandledRejection', (e) => console.error('unhandledRejection:', e));
process.on('uncaughtException', (e) => console.error('uncaughtException:', e));

const store = new MemoryStore();
const hub = new Hub(store);
const app = express();
app.disable('x-powered-by');
app.set('trust proxy', 1);   // behind Render's proxy: req.ip must be the real client, not the proxy (rate limits)
app.get('/health', (_req, res) => res.json({ ok: true }));
app.use('/api', buildApi(store, hub));
const server = http.createServer(app);
attachWebSocket(server, store, hub);
setInterval(() => store.purgeHistory(Date.now() - cfg.historyRetentionDays * 86400000), 3600000).unref();
server.listen(cfg.port, () => console.log(`TRACKER backend on :${cfg.port}`));
