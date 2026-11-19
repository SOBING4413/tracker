# API (all under /api, JSON, `Authorization: Bearer <jwt>` except auth)
POST /auth/register {email,password(>=8),displayName} · POST /auth/login · GET/DELETE /me
POST /groups {name} · GET /groups · POST /groups/join {code} · GET /groups/:id
PATCH /groups/:id {captainId?,sweeperId?,thresholdM?} (captain/owner) · DELETE /groups/:id/members/:uid · POST /groups/:id/end · GET /groups/:id/history
## WebSocket `/ws?token=JWT&group=ID`
Client→server: location{lat,lng,speed,heading} · share{on} · ptt_start · ptt_stop · ack{userId} · sos{message} · signal{to,payload} · binary audio frames (floor holder only)
Server→client: snapshot · location · presence · floor · alert{type: separated|repeat|rejoined, userId, name, distanceM} · ack · sos · ptt_result · error
