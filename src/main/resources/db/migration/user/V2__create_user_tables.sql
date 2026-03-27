-- ============================================================
-- V2 — User module: user_profile, user_preferences
-- ============================================================

-- ── user_profile ──────────────────────────────────────────
-- Extended user profile information
CREATE TABLE user_profile (
    id                    UUID         NOT NULL DEFAULT uuid_generate_v4(),
    user_id               UUID         NOT NULL,
    phone_number          VARCHAR(20),
    country               VARCHAR(2),
    currency              VARCHAR(3)   NOT NULL DEFAULT 'USD',
    timezone              VARCHAR(50),
    language              VARCHAR(10)  NOT NULL DEFAULT 'en',
    profile_picture_url   VARCHAR(1000),
    bio                   TEXT,
    notification_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_profile   PRIMARY KEY (id),
    CONSTRAINT uq_user_profile   UNIQUE (user_id),
    CONSTRAINT fk_profile_user   FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_profile_user ON user_profile (user_id);

-- ── user_preferences ──────────────────────────────────────
-- User application preferences
CREATE TABLE user_preferences (
    id                    UUID         NOT NULL DEFAULT uuid_generate_v4(),
    user_id               UUID         NOT NULL,
    theme                 VARCHAR(20)  NOT NULL DEFAULT 'light',
    two_factor_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    email_notifications   BOOLEAN      NOT NULL DEFAULT TRUE,
    weekly_digest         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_preferences   PRIMARY KEY (id),
    CONSTRAINT uq_user_preferences   UNIQUE (user_id),
    CONSTRAINT fk_preferences_user   FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_preferences_user ON user_preferences (user_id);
