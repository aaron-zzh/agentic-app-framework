package com.xuejiai.aaf.module.pay.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.pay.PayRefundStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 退款单 */
@Getter
@Setter
@Entity
@Table(name = "refund_order")
@SQLDelete(
        sql =
                "UPDATE refund_order SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class RefundOrder extends BaseEntity {

    /** 退款单号（唯一） */
    @Column(name = "refund_no", nullable = false, length = 64, unique = true)
    private String refundNo;

    /** 关联支付单 ID */
    @Column(name = "pay_order_id", nullable = false)
    private Long payOrderId;

    /** 商户订单号 */
    @Column(name = "merchant_order_no", nullable = false, length = 64)
    private String merchantOrderNo;

    /** 支付渠道编码 */
    @Column(name = "channel_code", nullable = false, length = 32)
    private String channelCode;

    /** 退款金额（分） */
    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    /** 退款状态 */
    @Column(name = "status", nullable = false)
    private Integer status = PayRefundStatusEnum.WAITING.getCode();

    /** 退款原因 */
    @Column(name = "reason", length = 500)
    private String reason;

    /** 渠道退款单号 */
    @Column(name = "channel_refund_no", length = 128)
    private String channelRefundNo;

    /** 退款成功时间 */
    @Column(name = "success_time")
    private LocalDateTime successTime;
}
