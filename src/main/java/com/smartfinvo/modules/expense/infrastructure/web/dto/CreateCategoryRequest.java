package com.smartfinvo.modules.expense.infrastructure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
    private String name;

    @Size(max = 50, message = "Icon must be less than 50 characters")
    private String icon;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$|^$", message = "Color must be a valid hex code (e.g., #FF5733) or empty")
    private String color;
}
