package com.smartgrocery.modules.ai.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * AiConversation — one turn in a conversation.
 *
 * One row = one message (either USER or ASSISTANT).
 * Grouped by sessionId — all messages in one chat share the same sessionId.
 * Feature column tells us which AI feature generated this turn.
 */
@Table("ai_conversation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversation {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("session_id")
    private String sessionId;

    // Which AI feature: NLP_SEARCH, RECIPE, SUGGESTION, BUDGET
    @Column("feature")
    private String feature;

    // USER or ASSISTANT
    @Column("role")
    private String role;

    @Column("content")
    private String content;

    @Column("tokens_used")
    private Integer tokensUsed;

    @Column("created_at")
    private Instant createdAt;
}