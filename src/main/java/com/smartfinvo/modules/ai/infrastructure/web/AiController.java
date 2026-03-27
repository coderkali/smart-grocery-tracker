package com.smartfinvo.modules.ai.infrastructure.web;

import com.smartfinvo.modules.ai.api.AiModulePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Features", description = "AI-powered grocery management — natural language parsing, smart suggestions based on purchase history (RAG), conversational recipe assistance with chat memory, and budget analysis.")
public class AiController {

    private final AiModulePort aiModulePort;

    /**
     * POST /api/v1/ai/search
     * Natural language grocery list management.
     * Body: { "message": "add 2L milk and eggs", "listId": "any-uuid" }
     */
    @Operation(
        summary = "Natural language grocery management",
        description = """
            Parse free-text commands into structured grocery items using GPT-4o.

            Examples:
            - `"add 2L milk and a dozen eggs"`
            - `"remove the bread from my list"`
            - `"I need chicken breast for the weekend"`

            The AI extracts item name, quantity, unit, and category automatically.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Items parsed and added",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AiModulePort.NlpSearchResult.class),
                examples = @ExampleObject(value = """
                    {
                      "aiResponse": "Added 2 items to your list ✓",
                      "itemsAdded": [
                        {"name": "milk", "quantity": 2.0, "unit": "litre", "category": "dairy"},
                        {"name": "eggs", "quantity": 12.0, "unit": "piece", "category": "dairy"}
                      ],
                      "tokensUsed": 187
                    }"""))),
        @ApiResponse(responseCode = "500", description = "AI processing error")
    })
    @PostMapping("/search")
    public Mono<AiModulePort.NlpSearchResult> naturalLanguageSearch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Natural language message and target list ID",
                content = @Content(examples = @ExampleObject(value = """
                    {"message": "add 2L milk and a dozen eggs", "listId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"}""")))
            @RequestBody NlpSearchRequest request,
            @Parameter(hidden = true) @RequestAttribute(value = "userId", required = false) UUID userId) {

        // Use test userId if not authenticated
        UUID resolvedUserId = userId != null
                ? userId
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return aiModulePort.naturalLanguageSearch(
            new AiModulePort.NlpSearchCommand(
                    resolvedUserId,
                UUID.fromString(request.listId()),
                request.message(),
                UUID.randomUUID().toString() // sessionId
            )
        );
    }

    record NlpSearchRequest(String message, String listId) {}

    @Operation(
        summary = "Get smart item suggestions",
        description = """
            Analyses the user's purchase history using RAG (pgvector similarity search) and suggests
            items they typically buy but haven't added to the current list yet.

            Returns up to `maxSuggestions` items (default 5) with a confidence score and reasoning.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suggestions generated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AiModulePort.SuggestionResult.class),
                examples = @ExampleObject(value = """
                    {
                      "suggestions": [
                        {"name": "butter", "category": "dairy", "confidence": 0.92, "reason": "You buy this 90% of the time with milk"},
                        {"name": "bread", "category": "bakery", "confidence": 0.78, "reason": "Weekly staple in your shopping history"}
                      ],
                      "reasoning": "Based on your last 8 shopping trips, these items pair with what's already in your list.",
                      "tokensUsed": 312
                    }"""))),
        @ApiResponse(responseCode = "500", description = "AI processing error")
    })
    @PostMapping("/suggest")
    public Mono<ResponseEntity<AiModulePort.SuggestionResult>> getSmartSuggestions(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Current list context for suggestions",
                content = @Content(examples = @ExampleObject(value = """
                    {
                      "listId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                      "currentItems": ["milk", "eggs"],
                      "maxSuggestions": 5
                    }""")))
            @RequestBody SuggestionRequest request,
            @Parameter(hidden = true) @RequestAttribute(required = false) UUID userId) {

        UUID resolvedUserId = userId != null ? userId
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return aiModulePort.getSmartSuggestions(
                new AiModulePort.SuggestionCommand(
                        resolvedUserId,
                        request.listId(),
                        request.currentItems(),
                        request.maxSuggestions() > 0 ? request.maxSuggestions() : 5
                )
        ).map(ResponseEntity::ok);
    }

    public record SuggestionRequest(
            UUID listId,
            List<String> currentItems,
            int maxSuggestions
    ) {}

    @Operation(
        summary = "Conversational recipe assistant (streaming SSE)",
        description = """
            Multi-turn conversational AI that remembers your previous messages within a session (stored in Redis).

            Typical flow:
            1. User: `"what can I make with chicken and garlic?"`
            2. AI streams back recipe ideas
            3. User: `"add the ingredients for the first one"`
            4. AI recalls the recipe from turn 1 and adds each ingredient via tool calls

            **Response format:** `text/event-stream` — chunks arrive as Server-Sent Events.
            Use the same `sessionId` across turns to maintain conversation context.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Streaming SSE response",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
        @ApiResponse(responseCode = "500", description = "AI processing error")
    })
    @PostMapping(value = "/recipe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getRecipeSuggestion(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Recipe chat message with session context",
                content = @Content(examples = @ExampleObject(value = """
                    {
                      "listId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                      "userMessage": "what can I make with chicken and garlic?",
                      "sessionId": "session-abc-123",
                      "currentItems": ["chicken breast", "garlic", "olive oil"]
                    }""")))
            @RequestBody RecipeRequest request,
            @Parameter(hidden = true) @RequestAttribute(required = false) UUID userId) {

        UUID resolvedUserId = userId != null ? userId
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return aiModulePort.getRecipeSuggestion(
                new AiModulePort.RecipeCommand(
                        resolvedUserId,
                        request.listId(),
                        request.userMessage(),
                        request.sessionId(),
                        request.currentItems()
                )
        );
    }

    public record RecipeRequest(
            UUID listId,
            String userMessage,
            String sessionId,
            List<String> currentItems
    ) {}


    @Operation(
        summary = "Analyse spending and budget",
        description = """
            Retrieves spending embeddings from pgvector for the requested period,
            runs GPT-4o analysis, then passes the result through an **Evaluator** that verifies
            the AI's numbers are grounded in real data (prevents hallucinated totals).

            `period` accepted values: `last_month`, `last_3_months`, `last_year`
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Budget analysis report",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AiModulePort.BudgetAnalysisResult.class),
                examples = @ExampleObject(value = """
                    {
                      "summary": "You spent $342 last month — $42 over your $300 budget.",
                      "insights": [
                        {
                          "category": "snacks",
                          "finding": "Snack spending up 34% vs previous month",
                          "suggestion": "Consider a $40 snack budget",
                          "type": "OVERSPEND"
                        }
                      ],
                      "totalSpent": 342.00,
                      "budgetVariance": 42.00,
                      "evaluatorPassed": true,
                      "tokensUsed": 521
                    }"""))),
        @ApiResponse(responseCode = "500", description = "AI processing error")
    })
    @PostMapping("/budget")
    public Mono<ResponseEntity<AiModulePort.BudgetAnalysisResult>> analyzeBudget(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Period and monthly budget for analysis",
                content = @Content(examples = @ExampleObject(value = """
                    {"period": "last_month", "monthlyBudget": 300.00}""")))
            @RequestBody BudgetRequest request,
            @Parameter(hidden = true) @RequestAttribute(required = false) UUID userId) {

        UUID resolvedUserId = userId != null ? userId
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return aiModulePort.analyzeBudget(
                new AiModulePort.BudgetAnalysisCommand(
                        resolvedUserId,
                        request.period(),
                        request.monthlyBudget()
                )
        ).map(ResponseEntity::ok);
    }

    public record BudgetRequest(
            String period,
            BigDecimal monthlyBudget
    ) {}
}