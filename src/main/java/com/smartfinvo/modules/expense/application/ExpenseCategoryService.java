package com.smartfinvo.modules.expense.application;

import com.smartfinvo.modules.expense.domain.ExpenseCategory;
import com.smartfinvo.modules.expense.infrastructure.persistence.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository categoryRepository;

    /**
     * Create a new category for a user
     */
    @Transactional
    public Mono<ExpenseCategory> createCategory(UUID userId, ExpenseCategory category) {
        log.info("Creating category for user: {} with name: {}", userId, category.getName());

        // Set defaults
        category.setId(UUID.randomUUID());
        category.setUserId(userId);
        category.setIsActive(true);
        category.setCreatedAt(OffsetDateTime.now());
        category.setUpdatedAt(OffsetDateTime.now());

        // Validate name not already exists
        return categoryRepository.findByUserIdAndNameIgnoreCase(userId, category.getName())
                .flatMap(existing -> Mono.<ExpenseCategory>error(
                        new IllegalArgumentException("Category '" + category.getName() + "' already exists")
                ))
                .switchIfEmpty(categoryRepository.save(category))
                .doOnSuccess(saved -> log.info("Category created: {}", saved.getId()))
                .doOnError(err -> log.error("Failed to create category", err));
    }

    /**
     * Get category by ID
     */
    public Mono<ExpenseCategory> getCategoryById(UUID categoryId, UUID userId) {
        log.info("Fetching category: {} for user: {}", categoryId, userId);

        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Category not found")))
                .doOnError(err -> log.error("Failed to fetch category", err));
    }

    /**
     * Get all active categories for a user
     */
    public Flux<ExpenseCategory> getAllCategories(UUID userId) {
        log.info("Fetching all categories for user: {}", userId);

        return categoryRepository.findByUserIdAndIsActiveTrueOrderByNameAsc(userId)
                .doOnError(err -> log.error("Failed to fetch categories", err));
    }

    /**
     * Update a category
     */
    @Transactional
    public Mono<ExpenseCategory> updateCategory(UUID categoryId, UUID userId, ExpenseCategory updateData) {
        log.info("Updating category: {} for user: {}", categoryId, userId);

        return getCategoryById(categoryId, userId)
                .flatMap(existing -> {
                    if (updateData.getName() != null) {
                        existing.setName(updateData.getName());
                    }
                    if (updateData.getIcon() != null) {
                        existing.setIcon(updateData.getIcon());
                    }
                    if (updateData.getColor() != null) {
                        existing.setColor(updateData.getColor());
                    }

                    existing.setUpdatedAt(OffsetDateTime.now());

                    return categoryRepository.save(existing)
                            .doOnSuccess(saved -> log.info("Category updated: {}", saved.getId()));
                })
                .doOnError(err -> log.error("Failed to update category", err));
    }

    /**
     * Deactivate a category
     */
    @Transactional
    public Mono<ExpenseCategory> deactivateCategory(UUID categoryId, UUID userId) {
        log.info("Deactivating category: {} for user: {}", categoryId, userId);

        return getCategoryById(categoryId, userId)
                .flatMap(existing -> {
                    existing.deactivate();
                    existing.setUpdatedAt(OffsetDateTime.now());
                    return categoryRepository.save(existing)
                            .doOnSuccess(saved -> log.info("Category deactivated: {}", saved.getId()));
                })
                .doOnError(err -> log.error("Failed to deactivate category", err));
    }

    /**
     * Check if category exists
     */
    public Mono<Boolean> categoryExists(UUID categoryId, UUID userId) {
        return categoryRepository.existsByIdAndUserId(categoryId, userId);
    }
}
