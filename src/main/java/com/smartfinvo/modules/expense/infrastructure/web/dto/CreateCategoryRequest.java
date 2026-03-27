package com.smartfinvo.modules.expense.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for creating or updating an expense category")
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
    @Schema(description = "Category name (1–100 characters)", requiredMode = Schema.RequiredMode.REQUIRED, example = "Groceries")
    private String name;

    @Size(max = 50, message = "Icon must be less than 50 characters")
    @Schema(description = "Emoji or icon identifier for the category", example = "🛒")
    private String icon;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$|^$", message = "Color must be a valid hex code (e.g., #FF5733) or empty")
    @Schema(description = "Hex color code for UI display (e.g. #4CAF50) or empty string", example = "#4CAF50")
    private String color;
}
