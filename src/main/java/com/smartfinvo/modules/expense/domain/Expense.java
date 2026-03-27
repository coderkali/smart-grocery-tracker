package com.smartfinvo.modules.expense.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("expense")
public class Expense {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("category_id")
    private UUID categoryId;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("description")
    private String description;

    @Column("expense_date")
    private LocalDate expenseDate;

    @Column("payment_method")
    private String paymentMethod;

    @Column("tags")
    private String tags;

    @Column("receipt_url")
    private String receiptUrl;

    @Column("notes")
    private String notes;

    @Column("version")
    private Integer version;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("deleted_at")
    private OffsetDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }
}
