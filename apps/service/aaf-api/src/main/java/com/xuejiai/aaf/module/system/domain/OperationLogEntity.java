package com.xuejiai.aaf.module.system.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 操作日志实体（不可变记录）。 */
@Getter
@Setter
@NoArgsConstructor
@Immutable
@Entity
@Table(name = "sys_operation_log")
public class OperationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "biz_no", length = 100)
    private String bizNo;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_url", length = 500)
    private String requestUrl;

    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    @Column(name = "response_result", columnDefinition = "TEXT")
    private String responseResult;

    @Column(name = "ip", length = 50)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "success", nullable = false)
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
