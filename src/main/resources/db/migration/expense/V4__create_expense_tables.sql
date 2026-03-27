-- ============================================================
-- V2 — Expense module: expense, expense_category
-- ============================================================

-- ── expense_category ──────────────────────────────────────────
-- Predefined categories for expenses
CREATE TABLE expense_category (
    id                UUID         NOT NULL DEFAULT uuid_generate_v4(),
    user_id           UUID         NOT NULL,
    name              VARCHAR(100) NOT NULL,
    icon              VARCHAR(50),
    color             VARCHAR(7),
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_expense_category       PRIMARY KEY (id),
    CONSTRAINT uq_category_per_user      UNIQUE (user_id, name),
    CONSTRAINT fk_category_user          FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE INDEX idx_category_user ON expense_category (user_id)
    WHERE is_active = TRUE;

-- ── expense ───────────────────────────────────────────────────
-- Core expense tracking table
CREATE TABLE expense (
    id                UUID           NOT NULL DEFAULT uuid_generate_v4(),
    user_id           UUID           NOT NULL,
    category_id       UUID           NOT NULL,
    amount            NUMERIC(12,2)  NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'USD',
    description       TEXT,
    expense_date      DATE           NOT NULL,
    payment_method    VARCHAR(50),
    tags              VARCHAR(500),
    receipt_url       VARCHAR(1000),
    notes             TEXT,
    version           INTEGER        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,

    CONSTRAINT pk_expense           PRIMARY KEY (id),
    CONSTRAINT fk_expense_user      FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_category  FOREIGN KEY (category_id)
        REFERENCES expense_category (id) ON DELETE RESTRICT,
    CONSTRAINT chk_amount_positive  CHECK (amount > 0)
);

-- Index for user's expenses (most common query)
CREATE INDEX idx_expense_user_date ON expense (user_id, expense_date DESC)
    WHERE deleted_at IS NULL;

-- Index for filtering by category
CREATE INDEX idx_expense_category ON expense (category_id)
    WHERE deleted_at IS NULL;

-- Index for monthly aggregations
CREATE INDEX idx_expense_created ON expense (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ── budget_rule ───────────────────────────────────────────────
-- Budget limits per category
CREATE TABLE budget_rule (
    id                    UUID          NOT NULL DEFAULT uuid_generate_v4(),
    user_id               UUID          NOT NULL,
    category_id           UUID          NOT NULL,
    monthly_limit         NUMERIC(12,2) NOT NULL,
    alert_threshold_pct   SMALLINT      NOT NULL DEFAULT 80,
    is_active             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_budget_rule             PRIMARY KEY (id),
    CONSTRAINT uq_budget_per_category     UNIQUE (user_id, category_id),
    CONSTRAINT fk_budget_user             FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_category         FOREIGN KEY (category_id)
        REFERENCES expense_category (id) ON DELETE CASCADE,
    CONSTRAINT chk_limit_positive         CHECK (monthly_limit > 0),
    CONSTRAINT chk_threshold_valid        CHECK (alert_threshold_pct BETWEEN 0 AND 100)
);

CREATE INDEX idx_budget_user ON budget_rule (user_id)
    WHERE is_active = TRUE;
