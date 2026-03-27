package com.smartfinvo.modules.expense.infrastructure.web;

import com.smartfinvo.modules.expense.application.ExpenseService;
import com.smartfinvo.modules.expense.domain.Expense;
import com.smartfinvo.modules.expense.infrastructure.web.dto.CreateExpenseRequest;
import com.smartfinvo.modules.expense.infrastructure.web.dto.UpdateExpenseRequest;
import com.smartfinvo.modules.expense.infrastructure.web.dto.ExpenseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
public class ExpenseController {

    private final ExpenseService expenseService;

    /**
     * POST /api/v1/expenses - Create a new expense
     */
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
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ExpenseResponse>> getExpense(
            @PathVariable UUID id,
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
    @GetMapping(params = {"start_date", "end_date"})
    public Mono<ResponseEntity<Flux<ExpenseResponse>>> getExpensesByDateRange(
            @RequestParam LocalDate start_date,
            @RequestParam LocalDate end_date,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        Flux<ExpenseResponse> expenses = expenseService.getExpensesByDateRange(userId, start_date, end_date)
                .map(this::toResponse);

        return Mono.just(ResponseEntity.ok(expenses));
    }

    /**
     * GET /api/v1/expenses?category_id=uuid - Get expenses by category
     */
    @GetMapping(params = "category_id")
    public Mono<ResponseEntity<Flux<ExpenseResponse>>> getExpensesByCategory(
            @RequestParam UUID category_id,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        Flux<ExpenseResponse> expenses = expenseService.getExpensesByCategory(userId, category_id)
                .map(this::toResponse);

        return Mono.just(ResponseEntity.ok(expenses));
    }

    /**
     * PUT /api/v1/expenses/:id - Update expense
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<ExpenseResponse>> updateExpense(
            @PathVariable UUID id,
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
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteExpense(
            @PathVariable UUID id,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        return expenseService.deleteExpense(id, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .doOnError(err -> log.error("Error deleting expense", err));
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
