package com.smartfinvo.modules.expense.infrastructure.web;

import com.smartfinvo.modules.expense.application.ExpenseCategoryService;
import com.smartfinvo.modules.expense.domain.ExpenseCategory;
import com.smartfinvo.modules.expense.infrastructure.web.dto.CreateCategoryRequest;
import com.smartfinvo.modules.expense.infrastructure.web.dto.CategoryResponse;
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
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Expense category management — create and organise categories used to classify expenses. All endpoints require a valid JWT Bearer token.")
@SecurityRequirement(name = "BearerAuth")
public class CategoryController {

    private final ExpenseCategoryService categoryService;

    /**
     * POST /api/v1/categories - Create a new category
     */
    @Operation(summary = "Create a new category",
        description = "Creates an expense category for the authenticated user. The `color` field must be a valid hex code (e.g. `#FF5733`) or omitted.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Category created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CategoryResponse.class),
                examples = @ExampleObject(value = """
                    {
                      "id": "22222222-2222-2222-2222-222222222222",
                      "user_id": "11111111-1111-1111-1111-111111111111",
                      "name": "Groceries",
                      "icon": "🛒",
                      "color": "#4CAF50",
                      "is_active": true,
                      "created_at": "2026-03-27T10:00:00Z",
                      "updated_at": "2026-03-27T10:00:00Z"
                    }"""))),
        @ApiResponse(responseCode = "400", description = "Validation error — name blank or color not a hex code"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
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
    @Operation(summary = "Get category by ID", description = "Returns a single active category by UUID. Users can only retrieve their own categories.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Category found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CategoryResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Category not found or belongs to another user")
    })
    @GetMapping("/{id}")
    public Mono<ResponseEntity<CategoryResponse>> getCategory(
            @Parameter(description = "UUID of the category", required = true) @PathVariable UUID id,
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
    @Operation(summary = "List all active categories", description = "Returns all active (non-deactivated) categories for the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of categories",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
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
    @Operation(summary = "Update a category", description = "Replaces the name, icon, and color of an existing category.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Category updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CategoryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Category not found or belongs to another user")
    })
    @PutMapping("/{id}")
    public Mono<ResponseEntity<CategoryResponse>> updateCategory(
            @Parameter(description = "UUID of the category to update", required = true) @PathVariable UUID id,
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
    @Operation(summary = "Deactivate a category",
        description = "Soft-deactivates a category (`is_active = false`). The category no longer appears in list queries but existing expenses that reference it are unaffected.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Category deactivated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Category not found or belongs to another user")
    })
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteCategory(
            @Parameter(description = "UUID of the category to deactivate", required = true) @PathVariable UUID id,
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
