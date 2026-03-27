package com.smartfinvo.modules.expense.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class UpdateExpenseRequest {

    @JsonProperty("category_id")
    private UUID categoryId;

    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999.99", message = "Amount must be less than 1 million")
    private BigDecimal amount;

    @Size(max = 500, message = "Description must be less than 500 characters")
    private String description;

    @PastOrPresent(message = "Expense date cannot be in the future")
    @JsonProperty("expense_date")
    private LocalDate expenseDate;

    @Size(max = 50, message = "Payment method must be less than 50 characters")
    @JsonProperty("payment_method")
    private String paymentMethod;

    @Size(max = 500, message = "Tags must be less than 500 characters")
    private String tags;

    @Size(max = 1000, message = "Receipt URL must be less than 1000 characters")
    @JsonProperty("receipt_url")
    private String receiptUrl;

    @Size(max = 1000, message = "Notes must be less than 1000 characters")
    private String notes;
}
