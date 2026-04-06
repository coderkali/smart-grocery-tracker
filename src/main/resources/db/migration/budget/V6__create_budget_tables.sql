-- ============================================================
-- V6 — Budget module: budget
-- ============================================================
-- One row per budget the user defines.
-- Budgets are linked to a category and a time period.
-- currentSpent is never stored — calculated at read time by
-- summing matching expense rows (via BudgetService).
-- ============================================================

CREATE TABLE budget (
    id               UUID          NOT NULL DEFAULT uuid_generate_v4(),
    user_id          UUID          NOT NULL,

    -- Human-readable name, e.g. "Monthly Groceries"
    name             VARCHAR(100)  NOT NULL,

    -- Links to expense_category — spending in this category counts toward the budget
    category_id      UUID          NOT NULL,

    -- The spending cap for the period
    limit_amount     NUMERIC(12,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL DEFAULT 'USD',

    -- MONTHLY | QUARTERLY | YEARLY | CUSTOM
    period           VARCHAR(20)   NOT NULL,

    -- Inclusive date range for which expenses are counted
    start_date       DATE          NOT NULL,
    end_date         DATE,                    -- NULL allowed for open-ended budgets

    -- Alert fires when (currentSpent / limitAmount * 100) >= alert_threshold
    alert_threshold  SMALLINT      NOT NULL DEFAULT 80,   -- percentage 0–100
    alert_enabled    BOOLEAN       NOT NULL DEFAULT TRUE,

    -- Comma-separated channels: "email", "push", "sms"
    -- Stored as plain text to avoid JSON column complexity in R2DBC
    alert_channels   VARCHAR(255)  NOT NULL DEFAULT '',

    -- Optimistic locking — incremented by R2DBC on every save()
    version          INTEGER       NOT NULL DEFAULT 0,

    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at       TIMESTAMPTZ,

    CONSTRAINT pk_budget         PRIMARY KEY (id),
    CONSTRAINT fk_budget_user    FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT chk_budget_period CHECK (period IN ('MONTHLY', 'QUARTERLY', 'YEARLY', 'CUSTOM')),
    CONSTRAINT chk_alert_pct     CHECK (alert_threshold BETWEEN 0 AND 100),
    CONSTRAINT chk_limit_amount  CHECK (limit_amount > 0)
);

-- Fetch all budgets for a user (most common query)
CREATE INDEX idx_budget_user       ON budget (user_id)
    WHERE deleted_at IS NULL;

-- Filter budgets by category (used when calculating budget-vs-actual)
CREATE INDEX idx_budget_category   ON budget (user_id, category_id)
    WHERE deleted_at IS NULL;

-- Filter active budgets by date range (used in analytics)
CREATE INDEX idx_budget_dates      ON budget (user_id, start_date, end_date)
    WHERE deleted_at IS NULL;
