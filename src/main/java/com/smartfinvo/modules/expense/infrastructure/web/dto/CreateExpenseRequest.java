package com.smartfinvo.modules.expense.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for creating a new expense")
public class CreateExpenseRequest {

    @NotNull(message = "Category ID is required")
    @JsonProperty("category_id")
    @Schema(description = "UUID of the expense category", requiredMode = Schema.RequiredMode.REQUIRED, example = "22222222-2222-2222-2222-222222222222")
    private UUID categoryId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999.99", message = "Amount must be less than 1 million")
    @Schema(description = "Expense amount (0.01–999999.99)", requiredMode = Schema.RequiredMode.REQUIRED, example = "45.99")
    private BigDecimal amount;

    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter code (e.g., USD)")
    @Schema(description = "ISO 4217 currency code. Defaults to USD if omitted.", example = "USD")
    private String currency;

    @Size(max = 500, message = "Description must be less than 500 characters")
    @Schema(description = "Human-readable description of the expense", example = "Weekly grocery run")
    private String description;

    @NotNull(message = "Expense date is required")
    @PastOrPresent(message = "Expense date cannot be in the future")
    @JsonProperty("expense_date")
    @Schema(description = "Date when the expense occurred (cannot be in the future)", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-03-27")
    private LocalDate expenseDate;

    @Size(max = 50, message = "Payment method must be less than 50 characters")
    @JsonProperty("payment_method")
    @Schema(description = "Payment method used", example = "credit_card")
    private String paymentMethod;

    @Size(max = 500, message = "Tags must be less than 500 characters")
    @Schema(description = "Comma-separated tags for filtering", example = "groceries,weekly")
    private String tags;

    @Size(max = 1000, message = "Receipt URL must be less than 1000 characters")
    @JsonProperty("receipt_url")
    @Schema(description = "URL to an uploaded receipt image", example = "https://storage.example.com/receipts/abc123.jpg")
    private String receiptUrl;

    @Size(max = 1000, message = "Notes must be less than 1000 characters")
    @Schema(description = "Additional free-text notes", example = "Bought extra milk for the weekend")
    private String notes;
}
