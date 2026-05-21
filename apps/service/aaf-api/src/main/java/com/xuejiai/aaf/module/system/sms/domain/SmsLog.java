package com.xuejiai.aaf.module.system.sms.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 短信发送日志（不可变，只追加）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_sms_log")
public class SmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 100)
    private String templateCode;

    @Column(columnDefinition = "TEXT")
    private String params;

    @Column(nullable = false, length = 20)
    private String provider;

    /** 0=待发送 1=成功 2=失败 */
    @Column(nullable = false)
    private Short sendStatus = 0;

    private LocalDateTime sendTime;

    @Column(length = 128)
    private String apiRequestId;

    @Column(length = 64)
    private String apiCode;

    @Column(length = 512)
    private String apiMsg;

    private LocalDateTime createdAt;
}
