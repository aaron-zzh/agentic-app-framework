package com.xuejiai.aaf.module.system.user.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * OAuth 第三方账号绑定。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user_oauth")
@SQLDelete(
        sql =
                "UPDATE sys_user_oauth SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class UserOauth extends BaseEntity {

    /** 关联用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** OAuth 提供商：wechat/wecom/dingtalk */
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    /** 第三方用户 ID */
    @Column(name = "provider_user_id", nullable = false, length = 200)
    private String providerUserId;

    /** 第三方用户名 */
    @Column(name = "provider_username", length = 200)
    private String providerUsername;

    /** 访问令牌 */
    @Column(name = "access_token", length = 500)
    private String accessToken;

    /** 刷新令牌 */
    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    /** 令牌过期时间 */
    @Column(name = "token_expire_time")
    private LocalDateTime tokenExpireTime;
}
