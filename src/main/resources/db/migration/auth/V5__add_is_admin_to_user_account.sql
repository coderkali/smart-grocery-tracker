-- ============================================================
-- V5 — Add is_admin flag to user_account
-- ============================================================
-- Adds a simple boolean admin flag to user_account.
-- Default FALSE — no existing user is promoted to admin automatically.
-- Admin status is granted manually via direct DB update or a future
-- admin-management endpoint. The GET /api/v1/users/{userId} endpoint
-- uses this flag to restrict access to admin-only users.
-- ============================================================

ALTER TABLE user_account
    ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial index — admins are rare, so this index is tiny but useful
-- for any future admin-listing queries without scanning the full table
CREATE INDEX idx_user_is_admin ON user_account (id)
    WHERE is_admin = TRUE;
