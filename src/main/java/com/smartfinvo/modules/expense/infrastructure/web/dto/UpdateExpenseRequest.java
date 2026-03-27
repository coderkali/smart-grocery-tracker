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
@Schema(description = "Request body for updating an existing expense. All fields are optional — only provided fields are changed.")
public class UpdateExpenseRequest {

    @JsonProperty("category_id")
    @Schema(description = "New category UUID", example = "22222222-2222-2222-2222-222222222222")
    private UUID categoryId;

    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999.99", message = "Amount must be less than 1 million")
    @Schema(description = "New amount (0.01–999999.99)", example = "52.50")
    private BigDecimal amount;

    @Size(max = 500, message = "Description must be less than 500 characters")
    @Schema(description = "Updated description", example = "Large weekly shop")
    private String description;

    @PastOrPresent(message = "Expense date cannot be in the future")
    @JsonProperty("expense_date")
    @Schema(description = "Corrected expense date", example = "2026-03-26")
    private LocalDate expenseDate;

    @Size(max = 50, message = "Payment method must be less than 50 characters")
    @JsonProperty("payment_method")
    @Schema(description = "Updated payment method", example = "debit_card")
    private String paymentMethod;

    @Size(max = 500, message = "Tags must be less than 500 characters")
    @Schema(description = "Updated comma-separated tags", example = "groceries,monthly")
    private String tags;

    @Size(max = 1000, message = "Receipt URL must be less than 1000 characters")
    @JsonProperty("receipt_url")
    @Schema(description = "Updated receipt URL", example = "https://storage.example.com/receipts/xyz789.jpg")
    private String receiptUrl;

    @Size(max = 1000, message = "Notes must be less than 1000 characters")
    @Schema(description = "Updated notes", example = "Corrected amount after price adjustment")
    private String notes;
}
