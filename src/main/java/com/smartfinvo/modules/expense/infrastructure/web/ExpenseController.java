package com.smartfinvo.modules.expense.infrastructure.web;

import com.smartfinvo.modules.expense.application.ExpenseService;
import com.smartfinvo.modules.expense.domain.Expense;
import com.smartfinvo.modules.expense.infrastructure.web.dto.BulkCreateExpenseRequest;
import com.smartfinvo.modules.expense.infrastructure.web.dto.BulkExpenseResponse;
import com.smartfinvo.modules.expense.infrastructure.web.dto.CreateExpenseRequest;
import com.smartfinvo.modules.expense.infrastructure.web.dto.UpdateExpenseRequest;
import com.smartfinvo.modules.expense.infrastructure.web.dto.ExpenseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Expense tracking — create, read, update, and delete expense records. All endpoints require a valid JWT Bearer token.")
@SecurityRequirement(name = "BearerAuth")
public class ExpenseController {

    private final ExpenseService expenseService;

    /**
     * POST /api/v1/expenses - Create a new expense
     */
    @Operation(summary = "Create a new expense",
        description = "Records a new expense for the authenticated user. `category_id` must reference an existing category owned by the user.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Expense created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ExpenseResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "category_id": "22222222-2222-2222-2222-222222222222",
                      "amount": 45.99,
                      "currency": "USD",
                      "description": "Weekly grocery run",
                      "expense_date": "2026-03-27",
                      "payment_method": "credit_card",
                      "tags": "groceries,weekly",
                      "receipt_url": null,
                      "notes": null,
                      "version": 0,
                      "created_at": "2026-03-27T10:00:00Z",
                      "updated_at": "2026-03-27T10:00:00Z"
                    }"""))),
        @ApiResponse(responseCode = "400", description = "Validation error — missing required fields or invalid values"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT")
    })
    @PostMapping
    public Mono<ResponseEntity<ExpenseResponse>> createExpense(
            @Valid @RequestBody CreateExpenseRequest request,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        Expense expense = Expense.builder()
                .categoryId(request.getCategoryId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .description(request.getDescription())
                .expenseDate(request.getExpenseDate())
                .paymentMethod(request.getPaymentMethod())
                .tags(request.getTags())
                .receiptUrl(request.getReceiptUrl())
                .notes(request.getNotes())
                .build();

        return expenseService.createExpense(userId, expense)
                .map(this::toResponse)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .doOnError(err -> log.error("Error creating expense", err));
    }

    /**
     * GET /api/v1/expenses/:id - Get expense by ID
     */
    @Operation(summary = "Get expense by ID", description = "Returns a single expense record by its UUID. Users can only retrieve their own expenses.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Expense found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ExpenseResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Expense not found or belongs to another user")
    })
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ExpenseResponse>> getExpense(
            @Parameter(description = "UUID of the expense", required = true) @PathVariable UUID id,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        return expenseService.getExpenseById(id, userId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .doOnError(err -> log.error("Error fetching expense", err));
    }

    /**
     * GET /api/v1/expenses - Get all expenses for user
     */
    @Operation(summary = "List all expenses", description = "Returns all expenses for the authenticated user, ordered by expense date descending.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of expenses",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                array = @ArraySchema(schema = @Schema(implementation = ExpenseResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public Mono<ResponseEntity<Flux<ExpenseResponse>>> getAllExpenses(
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        Flux<ExpenseResponse> expenses = expenseService.getAllExpenses(userId)
                .map(this::toResponse);

        return Mono.just(ResponseEntity.ok(expenses));
    }

    /**
     * GET /api/v1/expenses?start_date=2026-01-01&end_date=2026-03-31 - Get expenses by date range
     */
    @Operation(summary = "Filter expenses by date range",
        description = "Returns expenses whose `expense_date` falls within `[start_date, end_date]` inclusive. Both parameters are required. Format: `YYYY-MM-DD`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Filtered expenses",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                array = @ArraySchema(schema = @Schema(implementation = ExpenseResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Invalid date format"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping(params = {"start_date", "end_date"})
    public Mono<ResponseEntity<Flux<ExpenseResponse>>> getExpensesByDateRange(
            @Parameter(description = "Start date (inclusive), format YYYY-MM-DD", required = true, example = "2026-01-01") @RequestParam LocalDate start_date,
            @Parameter(description = "End date (inclusive), format YYYY-MM-DD", required = true, example = "2026-03-31") @RequestParam LocalDate end_date,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        Flux<ExpenseResponse> expenses = expenseService.getExpensesByDateRange(userId, start_date, end_date)
                .map(this::toResponse);

        return Mono.just(ResponseEntity.ok(expenses));
    }

    /**
     * GET /api/v1/expenses?category_id=uuid - Get expenses by category
     */
    @Operation(summary = "Filter expenses by category",
        description = "Returns all expenses for the authenticated user that belong to the specified category UUID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Expenses for the category",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                array = @ArraySchema(schema = @Schema(implementation = ExpenseResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Invalid UUID format"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping(params = "category_id")
    public Mono<ResponseEntity<Flux<ExpenseResponse>>> getExpensesByCategory(
            @Parameter(description = "UUID of the category to filter by", required = true) @RequestParam UUID category_id,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        Flux<ExpenseResponse> expenses = expenseService.getExpensesByCategory(userId, category_id)
                .map(this::toResponse);

        return Mono.just(ResponseEntity.ok(expenses));
    }

    /**
     * PUT /api/v1/expenses/:id - Update expense
     */
    @Operation(summary = "Update an expense",
        description = "Partially updates an existing expense. Only the fields provided in the request body are changed. Optimistic locking is applied via `version`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Expense updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ExpenseResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Expense not found or belongs to another user")
    })
    @PutMapping("/{id}")
    public Mono<ResponseEntity<ExpenseResponse>> updateExpense(
            @Parameter(description = "UUID of the expense to update", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateExpenseRequest request,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        Expense updateData = Expense.builder()
                .categoryId(request.getCategoryId())
                .amount(request.getAmount())
                .description(request.getDescription())
                .expenseDate(request.getExpenseDate())
                .paymentMethod(request.getPaymentMethod())
                .tags(request.getTags())
                .receiptUrl(request.getReceiptUrl())
                .notes(request.getNotes())
                .build();

        return expenseService.updateExpense(id, userId, updateData)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .doOnError(err -> log.error("Error updating expense", err));
    }

    /**
     * DELETE /api/v1/expenses/:id - Delete expense (soft delete)
     */
    @Operation(summary = "Delete an expense",
        description = "Soft-deletes an expense by its UUID. The record is marked as deleted and excluded from future queries but not removed from the database.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Expense deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Expense not found or belongs to another user")
    })
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteExpense(
            @Parameter(description = "UUID of the expense to delete", required = true) @PathVariable UUID id,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        return expenseService.deleteExpense(id, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .doOnError(err -> log.error("Error deleting expense", err));
    }

    /**
     * POST /api/v1/expenses/bulk — Create multiple expenses in one transaction
     * All items share the same receipt_date, vendor, and receipt_image_url.
     * The entire batch is rolled back if any item fails a DB constraint.
     * Must be declared before the generic @PostMapping to avoid route conflict.
     */
    @Operation(
            summary = "Bulk create expenses",
            description = """
                    Creates multiple expense records in a single database transaction.
                    All items share `receipt_date`, `vendor`, and `receipt_image_url`.
                    If any item fails (e.g. invalid category), the entire batch is rolled back.
                    Maximum 100 items per request.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "All expenses created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BulkExpenseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error — check items array and receipt_date"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/bulk")
    public Mono<ResponseEntity<BulkExpenseResponse>> bulkCreateExpenses(
            @Valid @RequestBody BulkCreateExpenseRequest request,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        return expenseService
                .bulkCreateExpenses(userId, request)
                .map(savedExpenses -> {
                    BulkExpenseResponse response = BulkExpenseResponse.builder()
                            .createdCount(savedExpenses.size())
                            .expenses(savedExpenses.stream().map(this::toResponse).toList())
                            .build();
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .onErrorResume(error -> {
                    log.error("POST /expenses/bulk — error userId={} error={}", userId, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Helper method to convert Expense domain to ExpenseResponse DTO
     */
    private ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .userId(expense.getUserId())
                .categoryId(expense.getCategoryId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .description(expense.getDescription())
                .expenseDate(expense.getExpenseDate())
                .paymentMethod(expense.getPaymentMethod())
                .tags(expense.getTags())
                .receiptUrl(expense.getReceiptUrl())
                .notes(expense.getNotes())
                .version(expense.getVersion())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
}
