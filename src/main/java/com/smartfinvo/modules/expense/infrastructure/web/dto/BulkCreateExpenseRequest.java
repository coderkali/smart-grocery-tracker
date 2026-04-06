package com.smartfinvo.modules.expense.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Request body for POST /api/v1/expenses/bulk
// Accepts a list of expense items and shared metadata (receipt date, vendor, image).
// OCR parsing is intentionally skipped — caller sends structured items directly.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for creating multiple expenses in one transaction.")
public class BulkCreateExpenseRequest {

    // At least 1 item required; each item is validated independently via @Valid
    @NotEmpty(message = "items must not be empty")
    @Size(max = 100, message = "Cannot create more than 100 expenses in a single bulk request")
    @Valid
    @Schema(description = "List of expense items to create (1–100)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<BulkExpenseItem> items;

    // Shared date applied to every item — the date the receipt or purchase was made
    @NotNull(message = "receipt_date is required")
    @PastOrPresent(message = "receipt_date cannot be in the future")
    @JsonProperty("receipt_date")
    @Schema(description = "Date of the receipt or purchase (YYYY-MM-DD, not in future)",
            example = "2026-04-04", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate receiptDate;

    // Optional shared fields that apply to all items in this bulk request
    @Size(max = 255, message = "vendor must be 255 characters or fewer")
    @Schema(description = "Vendor or store name applied to all items", example = "Whole Foods Market")
    private String vendor;

    @Size(max = 1000, message = "receipt_image_url must be 1000 characters or fewer")
    @JsonProperty("receipt_image_url")
    @Schema(description = "URL to the receipt image (uploaded separately)", example = "https://storage.example.com/receipts/abc.jpg")
    private String receiptImageUrl;

    // ── Inner DTO: one line item on the receipt ───────────────────────────
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "A single expense line item within a bulk request.")
    public static class BulkExpenseItem {

        @NotNull(message = "category_id is required for each item")
        @JsonProperty("category_id")
        @Schema(description = "Expense category UUID", example = "22222222-2222-2222-2222-222222222222",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private UUID categoryId;

        @NotNull(message = "amount is required for each item")
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        @DecimalMax(value = "999999.99", message = "amount must be less than 1 million")
        @Schema(description = "Item amount (0.01–999999.99)", example = "12.49",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal amount;

        @Size(max = 500, message = "description must be 500 characters or fewer")
        @Schema(description = "Item description", example = "Organic whole milk 2L")
        private String description;

        // Optional currency per item — defaults to USD if omitted
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        @Schema(description = "Currency code (ISO 4217). Defaults to USD.", example = "USD")
        private String currency;
    }
}
