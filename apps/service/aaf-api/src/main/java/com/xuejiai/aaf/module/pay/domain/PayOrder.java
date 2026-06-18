package com.xuejiai.aaf.module.pay.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 支付订单 */
@Getter
@Setter
@Entity
@Table(name = "pay_order")
@SQLDelete(
        sql = "UPDATE pay_order SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class PayOrder extends BaseEntity {

    /** 商户订单号（唯一） */
    @Column(name = "merchant_order_no", nullable = false, length = 64, unique = true)
    private String merchantOrderNo;

    /** 订单标题 */
    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    /** 订单描述 */
    @Column(name = "body", length = 500)
    private String body;

    /** 支付金额（分） */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** 订单状态 */
    @Column(name = "status", nullable = false)
    private Integer status = PayOrderStatusEnum.WAITING.getCode();

    /** 支付渠道编码 */
    @Column(name = "channel_code", nullable = false, length = 32)
    private String channelCode;

    /** 渠道订单号 */
    @Column(name = "channel_order_no", length = 128)
    private String channelOrderNo;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 过期时间 */
    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    /** 支付成功时间 */
    @Column(name = "success_time")
    private LocalDateTime successTime;

    /** 已退款金额（分） */
    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount = 0L;
}
