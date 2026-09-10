package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 订阅购买流水（新购/续费）。
 *
 * <p>标注 {@link OrgIgnore}：流水归属用户（{@code userId}）而非组织，{@code org_id} 恒为 NULL。
 */
@Getter
@Setter
@Entity
@Table(name = "subscription_record")
@CrudReference(
        key = "user",
        idProperty = "userId",
        targetResource = "system.user",
        viewField = "user")
@CrudReference(
        key = "plan",
        idProperty = "planId",
        targetResource = "billing.subscription-plan",
        viewField = "plan")
@CrudReference(
        key = "sku",
        idProperty = "skuId",
        targetResource = "billing.subscription-sku",
        viewField = "sku")
@SQLDelete(
        sql =
                "UPDATE subscription_record SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@OrgIgnore
public class SubscriptionRecord extends BaseEntity {

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 套餐 ID */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** SKU ID */
    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    /** 操作类型（NEW/UPGRADE/RENEW） */
    @Column(name = "operation", nullable = false, length = 16)
    private String operation;

    /** 关联支付订单 ID */
    @Column(name = "pay_order_id")
    private Long payOrderId;

    /** 实际收款金额（分） */
    @Column(name = "pay_price", nullable = false)
    private Long payPrice;

    /** 下单时目标 SKU 名义价格快照（分） */
    @Column(name = "sku_price_snapshot", nullable = false)
    private Long skuPriceSnapshot;

    /** 支付事实（UNPAID/PAID） */
    @Column(name = "pay_status", nullable = false, length = 16)
    private String payStatus = "UNPAID";

    /** 支付成功时间 */
    @Column(name = "pay_time")
    private LocalDateTime payTime;

    /** 履约状态（PENDING/FULFILLED/CLOSED/COMPENSATION_PENDING） */
    @Column(name = "fulfillment_status", nullable = false, length = 24)
    private String fulfillmentStatus = "PENDING";

    /** 下单时当前 ACTIVE Subscription ID；无订阅时为空。 */
    @Column(name = "checkout_generation_id")
    private Long checkoutGenerationId;

    /** 下单金额计算时刻，统一为数据库微秒精度。 */
    @Column(name = "checkout_calculated_at")
    private LocalDateTime checkoutCalculatedAt;

    /** 升级下单时的候选段和逐段剩余价值审计快照。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checkout_value_snapshot", columnDefinition = "jsonb")
    private String checkoutValueSnapshot;

    /** 服务世代，即承载本服务段的 Subscription ID。 */
    @Column(name = "service_generation_id")
    private Long serviceGenerationId;

    @Column(name = "service_start_at")
    private LocalDateTime serviceStartAt;

    @Column(name = "service_end_at")
    private LocalDateTime serviceEndAt;

    /** 名义价值状态（AVAILABLE/SUPERSEDED）。 */
    @Column(name = "value_status", length = 16)
    private String valueStatus;

    @Column(name = "superseded_by_record_id")
    private Long supersededByRecordId;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @Column(name = "exception_code", length = 48)
    private String exceptionCode;

    @Column(name = "exception_reason", length = 500)
    private String exceptionReason;

    @Column(name = "exception_detected_at")
    private LocalDateTime exceptionDetectedAt;

    @Column(name = "compensation_resolved_at")
    private LocalDateTime compensationResolvedAt;

    @Column(name = "compensation_result", length = 500)
    private String compensationResult;
}
