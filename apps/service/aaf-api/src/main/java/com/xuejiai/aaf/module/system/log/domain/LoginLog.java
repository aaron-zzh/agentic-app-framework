package com.xuejiai.aaf.module.system.log.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登录日志实体（不可变记录）。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@NoArgsConstructor
@Immutable
@Entity
@Table(name = "sys_login_log")
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID */
    @Column(name = "user_id")
    private Long userId;

    /** 用户名 */
    @Column(name = "username", length = 50)
    private String username;

    /** 登录类型：PASSWORD / EMAIL / OAUTH */
    @Column(name = "login_type", nullable = false, length = 20)
    private String loginType;

    /** 登录 IP */
    @Column(name = "ip", length = 50)
    private String ip;

    /** User-Agent */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** IP 归属地 */
    @Column(name = "location", length = 200)
    private String location;

    /** 是否成功 */
    @Column(name = "success", nullable = false)
    private Boolean success = true;

    /** 失败原因 */
    @Column(name = "fail_reason", length = 500)
    private String failReason;

    /** 登录时间 */
    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;
}
