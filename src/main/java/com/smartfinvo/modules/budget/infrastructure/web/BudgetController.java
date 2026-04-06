package com.smartfinvo.modules.budget.infrastructure.web;

import com.smartfinvo.modules.budget.application.BudgetService;
import com.smartfinvo.modules.budget.infrastructure.web.dto.BudgetResponse;
import com.smartfinvo.modules.budget.infrastructure.web.dto.CreateBudgetRequest;
import com.smartfinvo.modules.budget.infrastructure.web.dto.UpdateBudgetRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
@Tag(name = "Budgets", description = "Budget management — create and track spending limits per category and time period.")
@SecurityRequirement(name = "BearerAuth")
public class BudgetController {

    private final BudgetService budgetService;

    // ── POST /api/v1/budgets ──────────────────────────────────────────────
    @Operation(
            summary = "Create a budget",
            description = """
                    Creates a new spending budget for a category and time period.
                    `current_spent`, `remaining_amount`, and `percentage_used` in the response
                    will be 0 on creation — they reflect actual spending at read time.
                    If `end_date` is omitted, it is inferred from `period` (e.g., MONTHLY adds 1 month).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Budget created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BudgetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping
    public Mono<ResponseEntity<BudgetResponse>> createBudget(
            @Valid @RequestBody CreateBudgetRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        return budgetService
                .createBudget(userId, request)
                .map(budget -> ResponseEntity.status(HttpStatus.CREATED).body(budget))
                .onErrorResume(error -> {
                    log.error("POST /budgets — error userId={} error={}", userId, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ── GET /api/v1/budgets/{budgetId} ────────────────────────────────────
    @Operation(
            summary = "Get budget by ID",
            description = """
                    Returns a single budget including real-time `current_spent`, `remaining_amount`,
                    `percentage_used`, and `alert_status` calculated from matching expense records.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Budget found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BudgetResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Budget not found or belongs to another user")
    })
    @GetMapping("/{budgetId}")
    public Mono<ResponseEntity<BudgetResponse>> getBudget(
            @Parameter(description = "UUID of the budget", required = true)
            @PathVariable UUID budgetId,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        return budgetService
                .getBudgetById(userId, budgetId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    if ("BUDGET_NOT_FOUND".equals(error.getMessage())) {
                        log.warn("GET /budgets/{} — not found userId={}", budgetId, userId);
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    log.error("GET /budgets/{} — error userId={} error={}", budgetId, userId, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ── GET /api/v1/budgets ───────────────────────────────────────────────
    @Operation(
            summary = "List all budgets",
            description = """
                    Returns all active budgets for the authenticated user, ordered by creation date descending.
                    Each budget includes real-time `current_spent` and `alert_status`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of budgets",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = BudgetResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public Mono<ResponseEntity<Flux<BudgetResponse>>> listBudgets(
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        Flux<BudgetResponse> budgets = budgetService.listBudgets(userId);
        return Mono.just(ResponseEntity.ok(budgets));
    }

    // ── PATCH /api/v1/budgets/{budgetId} ──────────────────────────────────
    @Operation(
            summary = "Update a budget",
            description = """
                    Partially updates an existing budget. Only the fields you provide are changed.
                    You can update: `name`, `limit_amount`, `alert_threshold`, `alert_enabled`, `alert_channels`.
                    Category and dates cannot be changed after creation.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Budget updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BudgetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Budget not found or belongs to another user"),
            @ApiResponse(responseCode = "409", description = "Optimistic locking conflict — retry the request")
    })
    @PatchMapping("/{budgetId}")
    public Mono<ResponseEntity<BudgetResponse>> updateBudget(
            @Parameter(description = "UUID of the budget to update", required = true)
            @PathVariable UUID budgetId,
            @Valid @RequestBody UpdateBudgetRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        return budgetService
                .updateBudget(userId, budgetId, request)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    if ("BUDGET_NOT_FOUND".equals(error.getMessage())) {
                        log.warn("PATCH /budgets/{} — not found userId={}", budgetId, userId);
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    if (error instanceof org.springframework.dao.OptimisticLockingFailureException) {
                        log.warn("PATCH /budgets/{} — version conflict userId={}", budgetId, userId);
                        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
                    }
                    log.error("PATCH /budgets/{} — error userId={} error={}", budgetId, userId, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    // ── DELETE /api/v1/budgets/{budgetId} ─────────────────────────────────
    @Operation(
            summary = "Delete a budget",
            description = "Soft-deletes a budget. Existing expenses are not affected.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Budget deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Budget not found or belongs to another user")
    })
    @DeleteMapping("/{budgetId}")
    public Mono<ResponseEntity<Void>> deleteBudget(
            @Parameter(description = "UUID of the budget to delete", required = true)
            @PathVariable UUID budgetId,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {

        return budgetService
                .deleteBudget(userId, budgetId)
                .then(Mono.just(ResponseEntity.<Void>noContent().build()))
                .onErrorResume(error -> {
                    if ("BUDGET_NOT_FOUND".equals(error.getMessage())) {
                        log.warn("DELETE /budgets/{} — not found userId={}", budgetId, userId);
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    log.error("DELETE /budgets/{} — error userId={} error={}", budgetId, userId, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
