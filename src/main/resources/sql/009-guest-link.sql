ALTER TABLE webconference.room
    ADD COLUMN guest_link_enabled boolean NOT NULL DEFAULT FALSE,
    ADD COLUMN guest_token character varying(64),
    ADD COLUMN guest_session character varying(36);

CREATE UNIQUE INDEX room_guest_token_idx ON webconference.room (guest_token) WHERE guest_token IS NOT NULL;
CREATE INDEX room_guest_session_idx ON webconference.room (guest_session) WHERE guest_session IS NOT NULL;
