package com.smartfinvo.modules.budget.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// Maps to the budget table — one row per budget the user has defined.
// currentSpent, percentageUsed, and remainingAmount are NOT stored here —
// they are calculated at read time from the expense table by BudgetService.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("budget")
public class Budget {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("name")
    private String name;

    // The expense category this budget tracks spending for
    @Column("category_id")
    private UUID categoryId;

    @Column("limit_amount")
    private BigDecimal limitAmount;

    @Column("currency")
    private String currency;

    // MONTHLY | QUARTERLY | YEARLY | CUSTOM
    @Column("period")
    private String period;

    @Column("start_date")
    private LocalDate startDate;

    // NULL means open-ended — service uses LocalDate.now() as the upper bound
    @Column("end_date")
    private LocalDate endDate;

    // Percentage (0–100) at which an alert is triggered
    @Column("alert_threshold")
    private Short alertThreshold;

    @Column("alert_enabled")
    private Boolean alertEnabled;

    // Comma-separated channel list — "email,push"
    // Kept as plain String to avoid JSON handling complexity in R2DBC
    @Column("alert_channels")
    private String alertChannels;

    // @Version triggers R2DBC optimistic locking — incremented automatically on save()
    @Version
    @Column("version")
    private Integer version;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("deleted_at")
    private OffsetDateTime deletedAt;

    // ── Business logic ────────────────────────────────────────────────────

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    // Effective end date for expense sum queries —
    // open-ended budgets use today as the upper bound so currentSpent is current
    public LocalDate effectiveEndDate() {
        return endDate != null ? endDate : LocalDate.now();
    }
}
