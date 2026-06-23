package com.xuejiai.aaf.module.pay.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 支付通知任务——持久化可靠通知，失败指数退避重试 */
@Getter
@Setter
@Entity
@Table(name = "pay_notify_task")
public class PayNotifyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "pay_order_id", nullable = false)
    private Long payOrderId;

    @Column(name = "biz_order_type", nullable = false, length = 32)
    private String bizOrderType;

    /** PENDING / SUCCESS / FAILURE */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "notify_times", nullable = false)
    private Integer notifyTimes = 0;

    @Column(name = "max_notify_times", nullable = false)
    private Integer maxNotifyTimes = 8;

    @Column(name = "last_execute_time")
    private LocalDateTime lastExecuteTime;

    @Column(name = "next_notify_time", nullable = false)
    private LocalDateTime nextNotifyTime = LocalDateTime.now();

    @Column(name = "response", length = 512)
    private String response;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime = LocalDateTime.now();
}
