GRANT USAGE ON SCHEMA webconference TO "apps";
GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA webconference TO "apps";
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA webconference TO "apps";
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA webconference TO "apps";

GRANT USAGE ON SCHEMA webconference TO "apps";
ALTER DEFAULT PRIVILEGES IN SCHEMA webconference GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON TABLES TO "apps";
ALTER DEFAULT PRIVILEGES IN SCHEMA webconference GRANT EXECUTE ON FUNCTIONS TO "apps";
ALTER DEFAULT PRIVILEGES IN SCHEMA webconference GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO "apps";