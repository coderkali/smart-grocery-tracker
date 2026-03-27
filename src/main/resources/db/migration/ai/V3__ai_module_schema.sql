-- ══════════════════════════════════════════════════════════════════
-- V3 — AI Module Schema
-- ══════════════════════════════════════════════════════════════════

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- ── Vector store table ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS vector_store (
                                            id          UUID         DEFAULT gen_random_uuid() PRIMARY KEY,
    content     TEXT         NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}',
    embedding   VECTOR(1536) NOT NULL
    );

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS vector_store_metadata_user_idx
    ON vector_store USING gin (metadata);

-- ── AI Conversation history ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_conversation (
                                               id              UUID         DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         UUID         NOT NULL,
    session_id      VARCHAR(100) NOT NULL,
    feature         VARCHAR(50)  NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    tokens_used     INTEGER,
    created_at      TIMESTAMPTZ  DEFAULT now() NOT NULL
    );

CREATE INDEX IF NOT EXISTS ai_conv_user_idx     ON ai_conversation(user_id);
CREATE INDEX IF NOT EXISTS ai_conv_session_idx  ON ai_conversation(session_id);
CREATE INDEX IF NOT EXISTS ai_conv_feature_idx  ON ai_conversation(feature);