CREATE SCHEMA webconference;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE webconference.scripts
(
    filename character varying(255)      NOT NULL,
    passed   timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT scripts_pkey PRIMARY KEY (filename)
);

CREATE TABLE webconference.room
(
    id           character varying(36)       NOT NULL,
    name         character varying(250)      NOT NULL,
    owner        character varying(36)       NOT NULL,
    sessions     bigint                      NOT NULL DEFAULT 0,
    moderator_pw character varying(36)       NOT NULL,
    attendee_pw  character varying(36)       NOT NULL,
    link         character varying(250)      NOT NULL,
    created      timestamp without time zone NOT NULL default now(),
    CONSTRAINT room_pkey PRIMARY KEY (id)
);

CREATE TABLE webconference.session
(
    id         character varying(250)      NOT NULL,
    start_date date                        NOT NULL,
    start_time time without time zone      NOT NULL,
    end_date   date                        NOT NULL,
    end_time   time without time zone      NOT NULL,
    created    timestamp without time zone NOT NULL DEFAULT now(),
    meeting_id character varying(250)      NOT NULL,
    room_id    character varying(36)       NOT NULL,
    CONSTRAINT session_pkey PRIMARY KEY (id),
    CONSTRAINT fk_room_id FOREIGN KEY (room_id) REFERENCES webconference.room (id)
);
