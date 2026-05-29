package com.xuejiai.aaf.module.pay.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.pay.BizOrderStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 业务订单 */
@Getter
@Setter
@Entity
@Table(name = "biz_order")
@SQLDelete(
        sql =
                "UPDATE biz_order SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class BizOrder extends BaseEntity {

    /** 业务订单号（唯一） */
    @Column(name = "order_no", nullable = false, length = 64, unique = true)
    private String orderNo;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 订单类型：RECHARGE/PURCHASE/SUBSCRIPTION */
    @Column(name = "order_type", nullable = false, length = 32)
    private String orderType;

    /** 订单标题 */
    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    /** 订单总金额（分） */
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    /** 关联支付单 ID */
    @Column(name = "pay_order_id")
    private Long payOrderId;

    /** 订单状态 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = BizOrderStatusEnum.PENDING.getCode();
}
