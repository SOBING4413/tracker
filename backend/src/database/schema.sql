-- PostgreSQL schema for production use (the dev server uses an in-memory store with the same shape).
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  photo_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  invite_code TEXT UNIQUE NOT NULL,
  owner_id UUID NOT NULL REFERENCES users(id),
  captain_id UUID REFERENCES users(id),
  sweeper_id UUID REFERENCES users(id),
  threshold_m INT NOT NULL DEFAULT 1100 CHECK (threshold_m IN (500,1100,1500,2000)),
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ
);
CREATE TABLE group_members (
  group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  muted BOOLEAN NOT NULL DEFAULT false,   -- muted by the Road Captain (no voice, no chat)
  PRIMARY KEY (group_id, user_id)
);
CREATE TABLE chat_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,   -- deleting an account removes its messages
  body TEXT NOT NULL CHECK (char_length(body) <= 500),
  announce BOOLEAN NOT NULL DEFAULT false,                        -- captain announcement
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON chat_messages (group_id, created_at);
CREATE TABLE location_history (
  id BIGSERIAL PRIMARY KEY,
  group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  lat DOUBLE PRECISION NOT NULL, lng DOUBLE PRECISION NOT NULL,
  speed_mps REAL, heading REAL, recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ON location_history (group_id, recorded_at);
CREATE TABLE alerts (
  id BIGSERIAL PRIMARY KEY,
  group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  user_id UUID NOT NULL, kind TEXT NOT NULL, -- separated|rejoined|sos
  distance_m INT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
