package com.smartgrocery.modules.ai.infrastructure.persistence;

import com.smartgrocery.modules.ai.domain.AiConversation;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * AiConversationRepository — persists conversation history to PostgreSQL.
 *
 * Why persist if Redis already stores it?
 *
 *   Redis  → short term (1 hour TTL). Fast. Used by ChatMemory
 *            during active conversations.
 *
 *   PostgreSQL → permanent audit trail. Used for:
 *     - Showing user their full conversation history
 *     - Cost tracking (tokens_used column)
 *     - Future long-term memory / RAG on past conversations
 *     - Debugging AI behaviour
 */
public interface AiConversationRepository
        extends R2dbcRepository<AiConversation, UUID> {

    // Fetch all turns for a specific session — ordered by time
    Flux<AiConversation> findByUserIdAndSessionIdOrderByCreatedAtAsc(
            UUID userId, String sessionId);

    // Fetch recent conversations for a user across all sessions
    @Query("""
        SELECT * FROM ai_conversation
        WHERE user_id = :userId
        ORDER BY created_at DESC
        LIMIT :limit
        """)
    Flux<AiConversation> findRecentByUserId(UUID userId, int limit);

    // Count total tokens used by a user — for cost monitoring
    @Query("""
        SELECT COALESCE(SUM(tokens_used), 0)
        FROM ai_conversation
        WHERE user_id = :userId
        """)
    Mono<Long> sumTokensUsedByUserId(UUID userId);

    // Delete all conversations for a session — GDPR right to erasure
    Mono<Void> deleteByUserIdAndSessionId(UUID userId, String sessionId);
}