package com.smartfinvo.modules.expense.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("expense_category")
public class ExpenseCategory {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("name")
    private String name;

    @Column("icon")
    private String icon;

    @Column("color")
    private String color;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}
