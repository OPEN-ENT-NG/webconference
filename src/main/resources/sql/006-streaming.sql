ALTER TABLE webconference.room
    ADD COLUMN allow_streaming boolean DEFAULT FALSE,
    ADD COLUMN streaming_link character varying DEFAULT NULL,
    ADD COLUMN streaming_key character varying DEFAULT NULL;