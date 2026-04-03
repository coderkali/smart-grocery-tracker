# SmartFinvo Expense Management APIs - Testing Guide

**Status:** ✅ All APIs implemented and compiled  
**Date:** March 27, 2026  
**Endpoints:** 10 new endpoints (6 Expense + 4 Category)

---

## 📋 API Overview

### Expense Endpoints

| # | Method | Path | Description |
|---|--------|------|-------------|
| 1 | POST | /api/v1/expenses | Create new expense |
| 2 | GET | /api/v1/expenses | List all expenses |
| 3 | GET | /api/v1/expenses/:id | Get single expense |
| 4 | GET | /api/v1/expenses?start_date=X&end_date=Y | Filter by date range |
| 5 | GET | /api/v1/expenses?category_id=X | Filter by category |
| 6 | PUT | /api/v1/expenses/:id | Update expense |
| 7 | DELETE | /api/v1/expenses/:id | Delete expense |

### Category Endpoints

| # | Method | Path | Description |
|---|--------|------|-------------|
| 8 | POST | /api/v1/categories | Create new category |
| 9 | GET | /api/v1/categories | List all categories |
| 10 | GET | /api/v1/categories/:id | Get single category |
| 11 | PUT | /api/v1/categories/:id | Update category |
| 12 | DELETE | /api/v1/categories/:id | Delete category |

---

## 🔐 Authentication

All endpoints require JWT token in Authorization header:
```
Authorization: Bearer {jwt_token}
```

The user ID is extracted from the JWT token's `sub` claim.

---

## 📝 Request/Response Examples

### 1. Create Category

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {jwt_token}" \
  -d '{
    "name": "Groceries",
    "icon": "🛒",
    "color": "#FF5733"
  }'
```

**Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "Groceries",
  "icon": "🛒",
  "color": "#FF5733",
  "is_active": true,
  "created_at": "2026-03-27T08:15:30+00:00",
  "updated_at": "2026-03-27T08:15:30+00:00"
}
```

---

### 2. Get All Categories

**Request:**
```bash
curl -X GET http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer {jwt_token}"
```

**Response (200 OK):**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "user_id": "123e4567-e89b-12d3-a456-426614174000",
    "name": "Groceries",
    "icon": "🛒",
    "color": "#FF5733",
    "is_active": true,
    "created_at": "2026-03-27T08:15:30+00:00",
    "updated_at": "2026-03-27T08:15:30+00:00"
  }
]
```

---

### 3. Create Expense

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/expenses \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {jwt_token}" \
  -d '{
    "category_id": "550e8400-e29b-41d4-a716-446655440000",
    "amount": 45.99,
    "currency": "USD",
    "description": "Weekly grocery shopping",
    "expense_date": "2026-03-27",
    "payment_method": "credit_card",
    "tags": "food,weekly",
    "notes": "Bought vegetables and dairy"
  }'
```

**Response (201 Created):**
```json
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "user_id": "123e4567-e89b-12d3-a456-426614174000",
  "category_id": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 45.99,
  "currency": "USD",
  "description": "Weekly grocery shopping",
  "expense_date": "2026-03-27",
  "payment_method": "credit_card",
  "tags": "food,weekly",
  "receipt_url": null,
  "notes": "Bought vegetables and dairy",
  "version": 0,
  "created_at": "2026-03-27T08:20:15+00:00",
  "updated_at": "2026-03-27T08:20:15+00:00"
}
```

---

### 4. Get All Expenses

**Request:**
```bash
curl -X GET http://localhost:8080/api/v1/expenses \
  -H "Authorization: Bearer {jwt_token}"
```

---

### 5. Get Expenses by Date Range

**Request:**
```bash
curl -X GET "http://localhost:8080/api/v1/expenses?start_date=2026-03-01&end_date=2026-03-31" \
  -H "Authorization: Bearer {jwt_token}"
```

---

### 6. Update Expense

**Request:**
```bash
curl -X PUT http://localhost:8080/api/v1/expenses/770e8400-e29b-41d4-a716-446655440002 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {jwt_token}" \
  -d '{
    "amount": 50.50,
    "description": "Updated grocery shopping"
  }'
```

---

### 7. Delete Expense

**Request:**
```bash
curl -X DELETE http://localhost:8080/api/v1/expenses/770e8400-e29b-41d4-a716-446655440002 \
  -H "Authorization: Bearer {jwt_token}"
```

---

## 🔍 Input Validation

### Create Expense Validation Rules
- category_id: Required, must be UUID
- amount: Required, must be > 0 and < 1,000,000
- currency: Optional, default "USD"
- description: Optional, max 500 characters
- expense_date: Required, cannot be future date
- payment_method: Optional, max 50 characters
- tags: Optional, max 500 characters

### Create Category Validation Rules
- name: Required, 1-100 characters, unique per user
- icon: Optional, max 50 characters
- color: Optional, must be valid hex code

---

## 📊 Database Tables Created

### expense table
- id, user_id, category_id, amount, currency, description, expense_date, payment_method, tags, receipt_url, notes, version, created_at, updated_at, deleted_at

### expense_category table
- id, user_id, name, icon, color, is_active, created_at, updated_at

### budget_rule table
- id, user_id, category_id, monthly_limit, alert_threshold_pct, is_active, created_at, updated_at

---

## ✅ Implementation Complete

**What was built:**
- ✅ 2 Domain entities (Expense, ExpenseCategory)
- ✅ 2 Repositories (R2DBC)
- ✅ 2 Services (Business logic)
- ✅ 2 Controllers (REST endpoints)
- ✅ 4 DTOs (Request/Response objects)
- ✅ Database migrations (V2 user tables, V4 expense tables)
- ✅ Full input validation
- ✅ Reactive/async implementation
- ✅ Soft delete support
- ✅ Comprehensive filtering

**Total: 12 Java classes + SQL migrations**

