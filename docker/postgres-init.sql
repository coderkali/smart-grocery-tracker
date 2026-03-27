-- Docker init script — runs once when the container is first created
-- Creates extensions needed by Flyway migrations

\c smart_grocery;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Create a read-only user for analytics/reporting queries
CREATE USER grocery_readonly WITH PASSWORD 'readonly_secret';
GRANT CONNECT ON DATABASE smart_grocery TO grocery_readonly;
GRANT USAGE ON SCHEMA public TO grocery_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO grocery_readonly;
