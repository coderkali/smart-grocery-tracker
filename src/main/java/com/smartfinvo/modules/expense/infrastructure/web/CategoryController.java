package com.smartfinvo.modules.expense.infrastructure.web;

import com.smartfinvo.modules.expense.application.ExpenseCategoryService;
import com.smartfinvo.modules.expense.domain.ExpenseCategory;
import com.smartfinvo.modules.expense.infrastructure.web.dto.CreateCategoryRequest;
import com.smartfinvo.modules.expense.infrastructure.web.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ExpenseCategoryService categoryService;

    /**
     * POST /api/v1/categories - Create a new category
     */
    @PostMapping
    public Mono<ResponseEntity<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        ExpenseCategory category = ExpenseCategory.builder()
                .name(request.getName())
                .icon(request.getIcon())
                .color(request.getColor())
                .build();

        return categoryService.createCategory(userId, category)
                .map(this::toResponse)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .doOnError(err -> log.error("Error creating category", err));
    }

    /**
     * GET /api/v1/categories/:id - Get category by ID
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<CategoryResponse>> getCategory(
            @PathVariable UUID id,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        return categoryService.getCategoryById(id, userId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .doOnError(err -> log.error("Error fetching category", err));
    }

    /**
     * GET /api/v1/categories - Get all active categories for user
     */
    @GetMapping
    public Mono<ResponseEntity<Flux<CategoryResponse>>> getAllCategories(
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        Flux<CategoryResponse> categories = categoryService.getAllCategories(userId)
                .map(this::toResponse);

        return Mono.just(ResponseEntity.ok(categories));
    }

    /**
     * PUT /api/v1/categories/:id - Update category
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<CategoryResponse>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCategoryRequest request,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        ExpenseCategory updateData = ExpenseCategory.builder()
                .name(request.getName())
                .icon(request.getIcon())
                .color(request.getColor())
                .build();

        return categoryService.updateCategory(id, userId, updateData)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .doOnError(err -> log.error("Error updating category", err));
    }

    /**
     * DELETE /api/v1/categories/:id - Deactivate category
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteCategory(
            @PathVariable UUID id,
            Principal principal) {

        UUID userId = UUID.fromString(principal.getName());

        return categoryService.deactivateCategory(id, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .doOnError(err -> log.error("Error deleting category", err));
    }

    /**
     * Helper method to convert ExpenseCategory domain to CategoryResponse DTO
     */
    private CategoryResponse toResponse(ExpenseCategory category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .userId(category.getUserId())
                .name(category.getName())
                .icon(category.getIcon())
                .color(category.getColor())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
