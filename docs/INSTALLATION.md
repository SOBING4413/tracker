# Installation
## Backend
```
cd backend && cp .env.example .env   # set JWT_SECRET
npm install && npm test && npm start
```
Production: put it behind a TLS reverse proxy (nginx/Caddy) so clients use `https://` and `wss://`.
The dev server uses an in-memory store (data is lost on restart); `src/database/schema.sql` is the PostgreSQL schema
for a persistent store (a pg-backed implementation of `services/store.js` is still to be written).
## Android
Open the repository root in Android Studio (Hedgehog+, JDK 17) and let Gradle sync.
Emulator + local backend: default `trackerBaseUrl=http://10.0.2.2:8080`.
Build: `./gradlew assembleDebug -PtrackerBaseUrl=https://your.server` → `app/build/outputs/apk/debug/app-debug.apk`.
Release: configure a signing config from environment variables (never commit a keystore), then `./gradlew assembleRelease`.
