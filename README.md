# TRACKER — motorcycle group tracker & push-to-talk
Live GPS group map, automatic "rider left behind" alerts (default > 1.1 km, hysteresis), SOS, and push-to-talk for touring groups.

## Status (honest)
| Area | State |
|---|---|
| Alert engine (backend) | implemented, 10 unit tests pass |
| Hub: presence, location, alerts, SOS, PTT floor, audio relay | implemented, 7 unit tests pass |
| REST/WebSocket server, schema.sql | written; **not yet run** (deps not installable in authoring env) |
| Android app (Kotlin) | written; **not compiled or run on a device** |
| Persistent PostgreSQL store, Redis | not implemented (in-memory store) |
| WebRTC / Opus voice | not implemented (PCM over WebSocket instead) |
| Bluetooth runtime permission flow, light-theme polish, profile photos, trip-history UI | not done |
| Instrumented/integration tests | not written |

See `docs/` for installation, architecture, API and permissions. License: Apache-2.0.
