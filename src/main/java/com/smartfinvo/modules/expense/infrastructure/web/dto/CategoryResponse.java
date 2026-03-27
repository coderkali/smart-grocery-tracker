package com.smartfinvo.modules.expense.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private UUID id;

    @JsonProperty("user_id")
    private UUID userId;

    private String name;

    private String icon;

    private String color;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
}
