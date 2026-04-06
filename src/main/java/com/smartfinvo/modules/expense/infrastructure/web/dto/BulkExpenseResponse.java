package com.smartfinvo.modules.expense.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response returned after a successful bulk expense creation.")
public class BulkExpenseResponse {

    // How many rows were inserted — lets the client verify all items were saved
    @JsonProperty("created_count")
    @Schema(description = "Number of expense records created", example = "5")
    private int createdCount;

    // Full ExpenseResponse for each created row — same shape as POST /expenses
    @Schema(description = "The created expense records in insertion order")
    private List<ExpenseResponse> expenses;
}
