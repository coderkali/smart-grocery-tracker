-- ============================================================
-- V1 — Auth module: user_account, user_identity, refresh_token
-- ============================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── user_account ──────────────────────────────────────────────
-- Central identity table. One row per registered user.
CREATE TABLE user_account (
                              id                    UUID         NOT NULL DEFAULT uuid_generate_v4(),
                              email                 VARCHAR(255) NOT NULL,
                              email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
                              display_name          VARCHAR(255),
                              first_name            VARCHAR(100),
                              last_name             VARCHAR(100),
                              avatar_url            VARCHAR(1000),
                              status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                              failed_login_attempts SMALLINT     NOT NULL DEFAULT 0,
                              onboarding_step       VARCHAR(30)  NOT NULL DEFAULT 'ACCOUNT_CREATED',
                              version               INTEGER      NOT NULL DEFAULT 0,
                              created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                              updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                              deleted_at            TIMESTAMPTZ,

                              CONSTRAINT pk_user_account  PRIMARY KEY (id),
                              CONSTRAINT uq_user_email    UNIQUE (email),
                              CONSTRAINT chk_user_status  CHECK (status IN (
                                                                            'ACTIVE', 'SUSPENDED', 'LOCKED', 'DELETED'
                                  )),
                              CONSTRAINT chk_onboarding   CHECK (onboarding_step IN (
                                                                                     'ACCOUNT_CREATED', 'CARD_CONNECTED',
                                                                                     'CATEGORIES_SET', 'BUDGET_SET', 'COMPLETED'
                                  ))
);

-- Index for login lookup by email (most frequent query)
CREATE INDEX idx_user_email  ON user_account (email)
    WHERE deleted_at IS NULL;

-- Index for admin queries filtering by status
CREATE INDEX idx_user_status ON user_account (status)
    WHERE deleted_at IS NULL;

-- ── user_identity ─────────────────────────────────────────────
-- OAuth2 provider links. One user can have Google + GitHub both.
CREATE TABLE user_identity (
                               id              UUID         NOT NULL DEFAULT uuid_generate_v4(),
                               user_id         UUID         NOT NULL,
                               provider        VARCHAR(50)  NOT NULL,
                               provider_id     VARCHAR(255) NOT NULL,
                               provider_email  VARCHAR(255),
                               provider_name   VARCHAR(255),
                               avatar_url      VARCHAR(1000),
                               is_primary      BOOLEAN      NOT NULL DEFAULT FALSE,
                               created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                               CONSTRAINT pk_user_identity     PRIMARY KEY (id),
                               CONSTRAINT uq_provider_identity UNIQUE (provider, provider_id),
                               CONSTRAINT fk_identity_user     FOREIGN KEY (user_id)
                                   REFERENCES user_account (id) ON DELETE CASCADE
);

-- Index for joining identity back to user
CREATE INDEX idx_identity_user ON user_identity (user_id);

-- ── refresh_token ─────────────────────────────────────────────
-- Stores hashed refresh tokens. Never stores raw token values.
-- family column groups tokens for reuse detection —
-- if a revoked token is used, the entire family is revoked.
CREATE TABLE refresh_token (
                               id             UUID         NOT NULL DEFAULT uuid_generate_v4(),
                               user_id        UUID         NOT NULL,
                               token_hash     VARCHAR(255) NOT NULL,
                               family         UUID         NOT NULL,
                               device_hint    VARCHAR(255),
                               ip_address     VARCHAR(50),
                               expires_at     TIMESTAMPTZ  NOT NULL,
                               revoked_at     TIMESTAMPTZ,
                               revoke_reason  VARCHAR(50),
                               created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                               CONSTRAINT pk_refresh_token  PRIMARY KEY (id),
                               CONSTRAINT uq_token_hash     UNIQUE (token_hash),
                               CONSTRAINT fk_rt_user        FOREIGN KEY (user_id)
                                   REFERENCES user_account (id) ON DELETE CASCADE
);

-- Index for token lookup during refresh (hot path)
CREATE INDEX idx_rt_hash   ON refresh_token (token_hash);

-- Index for revoking all tokens by user on logout
CREATE INDEX idx_rt_user   ON refresh_token (user_id)
    WHERE revoked_at IS NULL;

-- Index for family-based reuse detection
CREATE INDEX idx_rt_family ON refresh_token (family);

-- Index for cleanup job — deletes expired tokens nightly
CREATE INDEX idx_rt_expiry ON refresh_token (expires_at)
    WHERE revoked_at IS NULL;