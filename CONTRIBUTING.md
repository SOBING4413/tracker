# Contributing
1. Fork, branch from `main`. 2. `cd backend && npm test` must pass; Android: `./gradlew testDebugUnitTest`.
3. Keep the alert engine in `backend/src/services/alertEngine.js` and `app/.../location/AlertEngine.kt` behaviourally identical (same test cases).
4. Never commit secrets, keystores or `.env`. Open a PR using the template.
