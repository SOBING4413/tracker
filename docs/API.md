# API (all under /api, JSON, `Authorization: Bearer <jwt>` except auth)
POST /auth/register {email,password(>=8),displayName} · POST /auth/login · GET/DELETE /me
POST /groups {name} · GET /groups · POST /groups/join {code} · GET /groups/:id
PATCH /groups/:id {captainId?,sweeperId?,thresholdM?} (captain/owner) · DELETE /groups/:id/members/:uid · POST /groups/:id/end · GET /groups/:id/history
## WebSocket `/ws?token=JWT&group=ID`
Client→server: location{lat,lng,speed,heading} · share{on} · ptt_start · ptt_stop · ack{userId} · sos{message} · signal{to,payload} · binary audio frames (floor holder only)
Server→client: snapshot · location · presence · floor · alert{type: separated|repeat|rejoined, userId, name, distanceM} · ack · sos · ptt_result · error

## Behaviour notes
- Rate limits (per IP / user): login 10/min, register 20/10 min, join-by-code 10/min per user → `429`. Invite codes are 10 hex chars.
- Malformed input → `400`, unexpected server error → `500 {error:"internal error"}`; the server never exits because of a request.
- WebSocket close codes: `4003` = rejected / removed from the group / group ended (client checks the group and stops tracking if it is really gone), `4000` = replaced by a newer connection of the same user.
- Kicking a member, a member leaving, ending a group or deleting an account disconnects the affected sockets immediately.
- Location history is thinned (one point per 30 s, or per 10 m moved) and capped (`MAX_HISTORY_POINTS`, default 200000; retention `HISTORY_RETENTION_DAYS`, default 7).
