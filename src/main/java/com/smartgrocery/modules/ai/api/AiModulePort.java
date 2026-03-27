package com.smartgrocery.modules.ai.api;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AiModulePort — internal contract for the AI module.
 *
 * Four features exposed:
 *   1. naturalLanguageSearch  — parse user text → add items to grocery list
 *   2. getSmartSuggestions    — RAG on purchase history → item suggestions
 *   3. getRecipeSuggestions   — chat with memory → recipe + add ingredients
 *   4. analyzeBudget          — RAG on spending → insights with Evaluator
 *
 * Nothing outside this module touches AiService directly.
 * All input/output types are defined here as sealed records.
 */
public interface AiModulePort {

    // ════════════════════════════════════════════════════════════════
    // Feature 1 — Natural Language Search
    // ════════════════════════════════════════════════════════════════

    /**
     * User sends free text like "add 2 litres of milk and a dozen eggs".
     * GPT-4o parses it and calls our Tool to extract structured items.
     */
    Mono<NlpSearchResult> naturalLanguageSearch(NlpSearchCommand command);

    record NlpSearchCommand(
            UUID   userId,
            UUID   listId,       // which grocery list to add items to
            String userMessage,  // raw text from user — "add 2L milk for weekend"
            String sessionId     // for conversation memory
    ) {}

    record NlpSearchResult(
            String        aiResponse,    // human readable reply — "Added 2 items ✓"
            List<ParsedItem> itemsAdded, // structured items the AI extracted
            int           tokensUsed
    ) {}

    record ParsedItem(
            String name,         // "milk"
            Double quantity,     // 2.0
            String unit,         // "litre"
            String category      // "dairy" — AI infers this
    ) {}

    // ════════════════════════════════════════════════════════════════
    // Feature 2 — Smart Suggestions (RAG)
    // ════════════════════════════════════════════════════════════════

    /**
     * Looks at user's purchase history vectors in pgvector.
     * Finds similar past shopping patterns.
     * GPT-4o suggests what they usually buy but haven't added yet.
     */
    Mono<SuggestionResult> getSmartSuggestions(SuggestionCommand command);

    record SuggestionCommand(
            UUID        userId,
            UUID        listId,
            List<String> currentItems,  // what's already in their list
            int         maxSuggestions  // how many to return — default 5
    ) {}

    record SuggestionResult(
            List<SuggestedItem> suggestions,
            String              reasoning,   // why AI suggested these
            int                 tokensUsed
    ) {}

    record SuggestedItem(
            String name,
            String category,
            Double confidence,   // 0.0 to 1.0 — how likely they need this
            String reason        // "You buy this 80% of the time with milk"
    ) {}

    // ════════════════════════════════════════════════════════════════
    // Feature 3 — Recipe Suggestions (Chat Memory)
    // ════════════════════════════════════════════════════════════════

    /**
     * Conversational feature — remembers what was said earlier.
     * User: "what can I make with chicken and garlic?"
     * AI:   "Lemon herb chicken — want me to add the ingredients?"
     * User: "yes add them"
     * AI remembers the recipe from turn 1 → calls addItem tool for each ingredient.
     *
     * Returns Flux<String> for streaming — user sees words appear as AI types.
     */
    Flux<String> getRecipeSuggestion(RecipeCommand command);

    record RecipeCommand(
            UUID        userId,
            UUID        listId,
            String      userMessage,   // current message in conversation
            String      sessionId,     // links messages in same conversation
            List<String> currentItems  // items already in their grocery list
    ) {}

    // ════════════════════════════════════════════════════════════════
    // Feature 4 — Budget Analysis (RAG + Evaluator)
    // ════════════════════════════════════════════════════════════════

    /**
     * Analyses spending patterns from vector store.
     * Evaluator verifies AI response is grounded in real data
     * — prevents hallucinated numbers in financial context.
     */
    Mono<BudgetAnalysisResult> analyzeBudget(BudgetAnalysisCommand command);

    record BudgetAnalysisCommand(
            UUID       userId,
            String     period,          // "last_month", "last_3_months", "last_year"
            BigDecimal monthlyBudget    // user's self-declared budget
    ) {}

    record BudgetAnalysisResult(
            String         summary,          // plain English overview
            List<Insight>  insights,         // specific actionable findings
            BigDecimal     totalSpent,
            BigDecimal     budgetVariance,   // positive = over budget
            boolean        evaluatorPassed,  // was AI response grounded in data?
            int            tokensUsed
    ) {}

    record Insight(
            String     category,    // "snacks", "produce", "dairy"
            String     finding,     // "Snack spending up 34% vs last month"
            String     suggestion,  // "Consider setting a snack budget of $40"
            InsightType type        // OVERSPEND, SAVING, PATTERN, ALERT
    ) {}

    enum InsightType {
        OVERSPEND,   // spending more than usual
        SAVING,      // spending less — positive
        PATTERN,     // recurring behaviour
        ALERT        // approaching or over budget
    }

    // ════════════════════════════════════════════════════════════════
    // Shared — Conversation history
    // ════════════════════════════════════════════════════════════════

    /**
     * Fetch past conversation turns for a session.
     * Used by frontend to restore chat history on page reload.
     */
    Flux<ConversationTurn> getConversationHistory(UUID userId, String sessionId);

    record ConversationTurn(
            String    role,       // "USER" or "ASSISTANT"
            String    content,
            String    feature,    // which AI feature this belongs to
            Instant   createdAt
    ) {}
}