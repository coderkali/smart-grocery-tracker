# SmartFinvo Endpoint Audit Report
Generated: 2026-04-04

---

## Summary

| Metric | Value |
|--------|-------|
| Total Endpoints in Spec | 23 |
| Total Endpoints Implemented (Spec) | 9 |
| Extra Endpoints (not in spec) | 9 |
| Missing Endpoints | 14 |
| Implementation % (spec coverage) | 39% |

---

## Found Endpoints (18 total — 9 matching spec + 9 extra)

### Authentication (`/api/v1/auth`)
Controller: `AuthController.java`
Class-level mapping: `@RequestMapping("/api/v1/auth")`

| # | HTTP | Path | Method | Request | Response | Spec Match |
|---|------|------|--------|---------|----------|------------|
| 1 | POST | `/api/v1/auth/refresh` | `refresh()` | HttpOnly cookie `refresh_token` | `{accessToken, userId, email, onboardingStep, expiresIn}` | ✅ |
| 2 | POST | `/api/v1/auth/logout` | `logout()` | JWT + `@RequestAttribute userId` | `{message}` | ✅ |
| 3 | POST | `/api/v1/auth/logout/all` | `logoutAll()` | JWT + `@RequestAttribute userId` | `{message, sessionsRevoked}` | ⭐ EXTRA |
| 4 | GET  | `/api/v1/auth/me` | `me()` | JWT + `@RequestAttribute userId` | `AuthUserDto` | ⭐ EXTRA |
| 5 | GET  | `/api/v1/auth/sessions` | `sessions()` | JWT + `@RequestAttribute userId` | `Flux<SessionDto>` | ⭐ EXTRA |
| 6 | DELETE | `/api/v1/auth/sessions/{sessionId}` | `revokeSession()` | `@PathVariable sessionId` | `{message}` | ⭐ EXTRA |

> **Note:** `POST /auth/google/initiate` and `POST /auth/google/callback` are handled by Spring Security OAuth2 filter chain at `/oauth2/authorization/google` — **not** via a controller method.

---

### Expense (`/api/v1/expenses`)
Controller: `ExpenseController.java`
Class-level mapping: `@RequestMapping("/api/v1/expenses")`

| # | HTTP | Path | Method | Request | Response | Spec Match |
|---|------|------|--------|---------|----------|------------|
| 7 | POST | `/api/v1/expenses` | `createExpense()` | `CreateExpenseRequest` | `ExpenseResponse` (201) | ✅ |
| 8 | GET  | `/api/v1/expenses/{id}` | `getExpense()` | `@PathVariable id` | `ExpenseResponse` | ✅ |
| 9 | GET  | `/api/v1/expenses` | `getAllExpenses()` | Principal | `Flux<ExpenseResponse>` | ✅ |
| 10 | GET  | `/api/v1/expenses?start_date=&end_date=` | `getExpensesByDateRange()` | Query params | `Flux<ExpenseResponse>` | ⭐ EXTRA |
| 11 | GET  | `/api/v1/expenses?category_id=` | `getExpensesByCategory()` | Query params | `Flux<ExpenseResponse>` | ⭐ EXTRA |
| 12 | PUT  | `/api/v1/expenses/{id}` | `updateExpense()` | `UpdateExpenseRequest` | `ExpenseResponse` | ✅ (spec says PATCH, impl uses PUT) |
| 13 | DELETE | `/api/v1/expenses/{id}` | `deleteExpense()` | `@PathVariable id` | 204 No Content | ✅ |

---

### Category (`/api/v1/categories`)
Controller: `CategoryController.java`
Class-level mapping: `@RequestMapping("/api/v1/categories")`
> **Not in original 23-endpoint spec — entirely extra domain**

| # | HTTP | Path | Method | Request | Response | Spec Match |
|---|------|------|--------|---------|----------|------------|
| 14 | POST | `/api/v1/categories` | `createCategory()` | `CreateCategoryRequest` | `CategoryResponse` (201) | ⭐ EXTRA |
| 15 | GET  | `/api/v1/categories/{id}` | `getCategory()` | `@PathVariable id` | `CategoryResponse` | ⭐ EXTRA |
| 16 | GET  | `/api/v1/categories` | `getAllCategories()` | Principal | `Flux<CategoryResponse>` | ⭐ EXTRA |
| 17 | PUT  | `/api/v1/categories/{id}` | `updateCategory()` | `CreateCategoryRequest` | `CategoryResponse` | ⭐ EXTRA |
| 18 | DELETE | `/api/v1/categories/{id}` | `deleteCategory()` | `@PathVariable id` | 204 No Content | ⭐ EXTRA |

---

### AI (`/api/v1/ai`)
Controller: `AiController.java`
Class-level mapping: `@RequestMapping("/api/v1/ai")`
> **Not in original 23-endpoint spec — entirely extra domain**

| # | HTTP | Path | Method | Request | Response | Spec Match |
|---|------|------|--------|---------|----------|------------|
| 19 | POST | `/api/v1/ai/search` | `naturalLanguageSearch()` | `NlpSearchRequest` | `NlpSearchResult` | ⭐ EXTRA |
| 20 | POST | `/api/v1/ai/suggest` | `getSmartSuggestions()` | `SuggestionRequest` | `SuggestionResult` | ⭐ EXTRA |
| 21 | POST | `/api/v1/ai/recipe` | `getRecipeSuggestion()` | `RecipeRequest` | `Flux<String>` SSE | ⭐ EXTRA |
| 22 | POST | `/api/v1/ai/budget` | `analyzeBudget()` | `BudgetRequest` | `BudgetAnalysisResult` | ⭐ EXTRA |

---

## Missing Endpoints (14 TODO)

### Authentication (2 TODO)
| # | HTTP | Path | Notes |
|---|------|------|-------|
| ⬜ | POST | `/api/v1/auth/google/initiate` | Currently handled by Spring Security filter, not a REST controller |
| ⬜ | POST | `/api/v1/auth/google/callback` | Currently handled by Spring Security OAuth2 handler, not a REST controller |

### User Management (4 TODO)
| # | HTTP | Path | Notes |
|---|------|------|-------|
| ⬜ | GET  | `/api/v1/users/me` | No `UserController` exists; partial overlap with `GET /api/v1/auth/me` |
| ⬜ | PATCH | `/api/v1/users/me` | Not implemented |
| ⬜ | GET  | `/api/v1/users/{userId}` | Not implemented |
| ⬜ | DELETE | `/api/v1/users/me` | Not implemented |

### Expense Management (1 TODO)
| # | HTTP | Path | Notes |
|---|------|------|-------|
| ⬜ | POST | `/api/v1/expenses/bulk` | Bulk import not implemented |

### Budget Management (5 TODO)
| # | HTTP | Path | Notes |
|---|------|------|-------|
| ⬜ | POST | `/api/v1/budgets` | No `BudgetController` exists |
| ⬜ | GET  | `/api/v1/budgets/{id}` | Not implemented |
| ⬜ | GET  | `/api/v1/budgets` | Not implemented |
| ⬜ | PATCH | `/api/v1/budgets/{id}` | Not implemented |
| ⬜ | DELETE | `/api/v1/budgets/{id}` | Not implemented |

### Analytics (4 TODO)
| # | HTTP | Path | Notes |
|---|------|------|-------|
| ⬜ | GET  | `/api/v1/analytics/summary` | No `AnalyticsController` exists |
| ⬜ | GET  | `/api/v1/analytics/trends` | Not implemented |
| ⬜ | GET  | `/api/v1/analytics/insights` | Not implemented |
| ⬜ | GET  | `/api/v1/analytics/budget-vs-actual` | Not implemented (requires Budget domain first) |

---

## Detailed Endpoint Findings

### ENDPOINT DETAIL — POST /api/v1/auth/refresh
- **Path:** `/api/v1/auth/refresh`
- **HTTP Method:** POST
- **Controller:** `AuthController.java`
- **Controller Method:** `refresh(ServerWebExchange exchange)`
- **Service Called:** `authService.refreshTokens(RefreshTokenCommand)`
- **Request DTO:** None — reads `refresh_token` HttpOnly cookie automatically
- **Response DTO:** `Map<String, Object>` → `{accessToken, userId, email, onboardingStep, expiresIn}`
- **Authentication Required:** NO (this IS the auth endpoint)
- **Database Operations:** SELECT (find token), UPDATE (rotate token), INSERT (new token)
- **Related Repository:** `RefreshTokenRepository`

---

### ENDPOINT DETAIL — POST /api/v1/auth/logout
- **Path:** `/api/v1/auth/logout`
- **HTTP Method:** POST
- **Controller:** `AuthController.java`
- **Controller Method:** `logout(ServerWebExchange exchange, UUID userId)`
- **Service Called:** `authService.logout(LogoutCommand)`
- **Request DTO:** None
- **Response DTO:** `Map<String, String>` → `{message}`
- **Authentication Required:** YES (JWT Bearer)
- **Database Operations:** UPDATE (revoke token), DELETE cookie
- **Related Repository:** `RefreshTokenRepository`

---

### ENDPOINT DETAIL — POST /api/v1/expenses
- **Path:** `/api/v1/expenses`
- **HTTP Method:** POST
- **Controller:** `ExpenseController.java`
- **Controller Method:** `createExpense(CreateExpenseRequest, Principal)`
- **Service Called:** `expenseService.createExpense(userId, expense)`
- **Request DTO:** `CreateExpenseRequest` — `{categoryId, amount, currency, description, expenseDate, paymentMethod, tags, receiptUrl, notes}`
- **Response DTO:** `ExpenseResponse` — all expense fields + `{id, version, createdAt, updatedAt}`
- **Authentication Required:** YES
- **Database Operations:** INSERT
- **Related Repository:** `ExpenseRepository`

---

### ENDPOINT DETAIL — PUT /api/v1/expenses/{id}
- **Path:** `/api/v1/expenses/{id}`
- **HTTP Method:** PUT  ⚠️ (spec says PATCH)
- **Controller:** `ExpenseController.java`
- **Controller Method:** `updateExpense(UUID id, UpdateExpenseRequest, Principal)`
- **Service Called:** `expenseService.updateExpense(id, userId, updateData)`
- **Request DTO:** `UpdateExpenseRequest` — all fields optional
- **Response DTO:** `ExpenseResponse`
- **Authentication Required:** YES
- **Database Operations:** SELECT + UPDATE (optimistic lock via `version`)
- **Related Repository:** `ExpenseRepository`

---

## Code Patterns Detected

### Controller Pattern
```java
@RestController
@RequestMapping("/api/v1/{domain}")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Domain Name")
public class DomainController {

    @PostMapping
    public Mono<ResponseEntity<DomainResponse>> create(
            @Valid @RequestBody CreateDomainRequest request,
            Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        return service.create(userId, request)
                .map(result -> ResponseEntity.status(201).body(result));
    }
}
```

### Service Pattern
- Application services receive `userId` + domain object
- Services delegate to domain repositories
- `@Transactional` on mutating methods
- All methods return `Mono<T>` or `Flux<T>` (reactive)

### Repository Pattern
- Extends Spring Data R2DBC `ReactiveCrudRepository<Entity, UUID>`
- Custom `@Query` annotations for complex queries (soft delete, date ranges)
- All queries include `userId` filter for multi-tenant isolation
- Soft deletes use `deleted_at` timestamp column

### Response Format
- Direct DTO responses (no wrapper like `ApiResponse<T>`)
- `ResponseEntity<T>` with explicit status codes
- 201 for POST creates, 204 for DELETEs, 200 for GET/PUT

### Error Handling
- Spring WebFlux `@ControllerAdvice` or inline `onErrorResume`
- 404 for not found / wrong user, 403 for unauthorized session access
- 400 for `@Valid` violations
- Reactive error propagation via `Mono.error()`

### Security
- JWT extracted via `@RequestAttribute("userId")` (set by JWT filter)
- `Principal principal` → `principal.getName()` returns userId string
- All controllers annotated `@SecurityRequirement(name = "BearerAuth")`

---

## Recommendations — Build Order

Based on dependencies between domains, build in this order:

### Phase 1 — User Domain (no dependencies)
1. `GET /api/v1/users/me` — read profile (overlaps with `GET /auth/me`, consolidate or alias)
2. `PATCH /api/v1/users/me` — update display name, preferences
3. `DELETE /api/v1/users/me` — account deletion
4. `GET /api/v1/users/{userId}` — admin/shared view

### Phase 2 — Budget Domain (no dependencies)
5. `POST /api/v1/budgets` — create budget
6. `GET /api/v1/budgets` — list budgets
7. `GET /api/v1/budgets/{id}` — get single budget
8. `PATCH /api/v1/budgets/{id}` — update budget
9. `DELETE /api/v1/budgets/{id}` — delete budget

### Phase 3 — Expense Completion
10. `POST /api/v1/expenses/bulk` — bulk import (depends on expense + category already done)

### Phase 4 — Analytics (depends on Budget + Expense)
11. `GET /api/v1/analytics/summary`
12. `GET /api/v1/analytics/trends`
13. `GET /api/v1/analytics/insights`
14. `GET /api/v1/analytics/budget-vs-actual` — requires Budget domain

### Notes
- `POST /auth/google/initiate` and `POST /auth/google/callback` are handled by Spring Security — may not need controller implementations
- `PUT /expenses/{id}` uses PUT not PATCH as the spec says — consider aligning annotation to `@PatchMapping` for spec compliance
- `GET /users/me` may be consolidatable with existing `GET /auth/me`

---

## Statistics

- **Total Endpoints in Spec:** 23
- **Total Endpoints Implemented (matching spec):** 9
- **Extra Endpoints (beyond spec):** 9 (logout/all, auth/me, sessions CRUD, category CRUD, AI features)
- **Missing from Spec:** 14
- **Implementation %:** 39%
- **Ready for Claude Code Generation:** 14 endpoints (User × 4, Budget × 5, Expense bulk × 1, Analytics × 4)
