package com.xuejiai.aaf.module.billing.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 订阅可售规格，独立定义周期和价格。 */
@Getter
@Setter
@Entity
@Table(name = "billing_subscription_plan_sku")
@CrudReference(
        key = "plan",
        idProperty = "planId",
        targetResource = "billing.subscription-plan",
        inputField = "planId",
        viewField = "plan")
@SQLDelete(
        sql =
                "UPDATE billing_subscription_plan_sku SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@OrgIgnore
public class SubscriptionSku extends BaseEntity {

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "sku_code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "billing_cycle", nullable = false, length = 16)
    private String billingCycle;

    @Column(name = "cycle_months", nullable = false)
    private Integer cycleMonths;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "market_price", nullable = false)
    private Long marketPrice;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "sort", nullable = false)
    private Integer sort;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ext", columnDefinition = "jsonb")
    private String ext;

    public boolean isFree() {
        return "FREE_DEFAULT".equals(code);
    }
}
