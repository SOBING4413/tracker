# TRACKER — motorcycle group tracker & push-to-talk
Live GPS group map, group chat, captain-only control panel, automatic "rider left behind" alerts (default > 1.1 km, hysteresis), SOS, and push-to-talk for touring groups.

## Status (honest)
| Area | State |
|---|---|
| Captain panel (members + details, mute, sweeper/captain, kick, announce), group chat, always-on mic | implemented (backend: 36 unit tests pass + smoke-tested over HTTP/WebSocket); **Android UI not compiled or run** |
| Alert engine (backend) | implemented, 10 unit tests pass |
| Hub: presence, location, alerts, SOS, PTT floor, audio relay | implemented, 7 unit tests pass |
| REST/WebSocket server, schema.sql | written; **not yet run** (deps not installable in authoring env) |
| Android app (Kotlin) | written; **not compiled or run on a device** |
| Persistent PostgreSQL store, Redis | not implemented (in-memory store) |
| WebRTC / Opus voice | not implemented (PCM over WebSocket instead) |
| Bluetooth runtime permission flow, light-theme polish, profile photos, trip-history UI | not done |
| Instrumented/integration tests | not written |

See `docs/` for installation, architecture, API and permissions. License: Apache-2.0.

## Credits

Made by [SOBING4413](https://github.com/SOBING4413) · [Source](https://github.com/SOBING4413/tracker) · [Discord](https://discord.gg/9nsub2yx4V)

## Languages

English and Bahasa Indonesia (Settings → Language, or the login screen). Strings live in `app/src/main/res/values/strings.xml` (English) and `values-in/strings.xml` (Indonesian).
