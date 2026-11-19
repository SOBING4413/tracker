'use strict';
const http = require('http');
const express = require('express');
const cfg = require('./config');
const { MemoryStore } = require('./services/store');
const { Hub } = require('./services/hub');
const { buildApi } = require('./controllers/api');
const { attachWebSocket } = require('./websocket');

const store = new MemoryStore();
const hub = new Hub(store);
const app = express();
app.disable('x-powered-by');
app.get('/health', (_req, res) => res.json({ ok: true }));
app.use('/api', buildApi(store, hub));
const server = http.createServer(app);
attachWebSocket(server, store, hub);
setInterval(() => store.purgeHistory(Date.now() - cfg.historyRetentionDays * 86400000), 3600000).unref();
server.listen(cfg.port, () => console.log(`TRACKER backend on :${cfg.port}`));
