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
    id             character varying(36)       NOT NULL,
    name           character varying(250)      NOT NULL,
    owner          character varying(36)       NOT NULL,
    sessions       bigint                      NOT NULL DEFAULT 0,
    moderator_pw   character varying(36)       NOT NULL,
    attendee_pw    character varying(36)       NOT NULL,
    link           character varying(250)      NOT NULL,
    created        timestamp without time zone NOT NULL default now(),
    active_session character varying(250),
    CONSTRAINT room_pkey PRIMARY KEY (id)
);

CREATE TABLE webconference.session
(
    id          character varying(36)       NOT NULL,
    start_date  date                        NOT NULL DEFAULT now(),
    start_time  time without time zone      NOT NULL DEFAULT now(),
    end_date    date,
    end_time    time without time zone,
    created     timestamp without time zone NOT NULL DEFAULT now(),
    internal_id character varying(250)      NOT NULL,
    room_id     character varying(36)       NOT NULL,
    CONSTRAINT session_pkey PRIMARY KEY (id),
    CONSTRAINT fk_room_id FOREIGN KEY (room_id) REFERENCES webconference.room (id) ON DELETE CASCADE
);

CREATE OR REPLACE FUNCTION webconference.set_active_session() RETURNS TRIGGER AS
$BODY$
BEGIN
    UPDATE webconference.room
    SET active_session = NEW.id
    WHERE id = NEW.room_id;

    RETURN NEW;
END;
$BODY$
    LANGUAGE plpgsql;

CREATE TRIGGER set_active_session AFTER INSERT ON webconference.session
    FOR EACH ROW EXECUTE PROCEDURE webconference.set_active_session();

CREATE OR REPLACE FUNCTION webconference.remove_active_session() RETURNS TRIGGER AS
$BODY$
BEGIN
    UPDATE webconference.room
    SET active_session = null
    WHERE id = NEW.room_id;

    RETURN NEW;
END;
$BODY$
    LANGUAGE plpgsql;

CREATE TRIGGER inactive_session AFTER INSERT OR UPDATE OF end_date ON webconference.session
    FOR EACH ROW
EXECUTE PROCEDURE webconference.remove_active_session();

CREATE OR REPLACE FUNCTION webconference.increment_room_sessions() RETURNS TRIGGER AS
$BODY$
BEGIN
    UPDATE webconference.room
    SET sessions = sessions + 1
    WHERE id = NEW.room_id;

    RETURN NEW;
END;
$BODY$
    LANGUAGE plpgsql;

CREATE TRIGGER increment_room_sessions AFTER INSERT ON webconference.session
    FOR EACH ROW EXECUTE PROCEDURE webconference.increment_room_sessions();