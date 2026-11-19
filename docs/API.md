# API (all under /api, JSON, `Authorization: Bearer <jwt>` except auth)
POST /auth/register {email,password(>=8),displayName} · POST /auth/login · GET/DELETE /me
POST /groups {name} · GET /groups · POST /groups/join {code} · GET /groups/:id
GET /groups/:id/admin (**Road Captain only**, 403 otherwise) → {group, members:[{id,name,email,role:captain|sweeper|member,isOwner,online,sharing,muted,lat,lng,speedKmh,lastSeenMs,distanceToCaptainM,joinedAt}], onlineCount}
POST /groups/:id/admin/mute {userId, muted:boolean} (**Road Captain only**; the captain cannot be muted) → {members}
PATCH /groups/:id {captainId?,sweeperId?,thresholdM?} (captain/owner) · DELETE /groups/:id/members/:uid · POST /groups/:id/end · GET /groups/:id/history
## WebSocket `/ws?token=JWT&group=ID`
Client→server: location{lat,lng,speed,heading} · share{on} · ptt_start · ptt_stop · ack{userId} · sos{message} · chat{text, announce?} · signal{to,payload} · binary audio frames (floor holder only)
Server→client: snapshot · location · presence · floor · alert{type: separated|repeat|rejoined, userId, name, distanceM} · ack · sos · ptt_result{granted,holder,muted?} · chat{id,userId,name,text,ts,announce} · muted{userId,muted} · group (roles/members changed: re-fetch GET /groups/:id) · error
`snapshot` now also carries `chat` (last 50 messages) and `muted` (ids).

## Behaviour notes
- Chat: max 500 characters, max 5 messages per 5 s per user (error `too many messages`), kept in memory (last 200 per group), removed with the group or with the author's account. `announce:true` is honoured only for the Road Captain.
- Muted members (set by the captain) cannot chat, request the floor or transmit audio; a muted speaker loses the floor immediately. A member who paused location sharing has no position in `/admin` (lat/lng null).
- Rate limits (per IP / user): login 10/min, register 20/10 min, join-by-code 10/min per user → `429`. Invite codes are 10 hex chars.
- Malformed input → `400`, unexpected server error → `500 {error:"internal error"}`; the server never exits because of a request.
- WebSocket close codes: `4003` = rejected / removed from the group / group ended (client checks the group and stops tracking if it is really gone), `4000` = replaced by a newer connection of the same user.
- Kicking a member, a member leaving, ending a group or deleting an account disconnects the affected sockets immediately.
- Location history is thinned (one point per 30 s, or per 10 m moved) and capped (`MAX_HISTORY_POINTS`, default 200000; retention `HISTORY_RETENTION_DAYS`, default 7).
