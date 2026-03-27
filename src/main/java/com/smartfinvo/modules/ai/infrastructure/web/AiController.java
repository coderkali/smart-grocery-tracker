package com.smartfinvo.modules.ai.infrastructure.web;

import com.smartfinvo.modules.ai.api.AiModulePort;
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
public class AiController {

    private final AiModulePort aiModulePort;

    /**
     * POST /api/v1/ai/search
     * Natural language grocery list management.
     * Body: { "message": "add 2L milk and eggs", "listId": "any-uuid" }
     */
    @PostMapping("/search")
    public Mono<AiModulePort.NlpSearchResult> naturalLanguageSearch(
            @RequestBody NlpSearchRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {

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

    @PostMapping("/suggest")
    public Mono<ResponseEntity<AiModulePort.SuggestionResult>> getSmartSuggestions(
            @RequestBody SuggestionRequest request,
            @RequestAttribute(required = false) UUID userId) {

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

    @PostMapping(value = "/recipe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getRecipeSuggestion(
            @RequestBody RecipeRequest request,
            @RequestAttribute(required = false) UUID userId) {

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


    @PostMapping("/budget")
    public Mono<ResponseEntity<AiModulePort.BudgetAnalysisResult>> analyzeBudget(
            @RequestBody BudgetRequest request,
            @RequestAttribute(required = false) UUID userId) {

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