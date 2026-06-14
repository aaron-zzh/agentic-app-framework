package com.xuejiai.aaf.module.system.user.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.xuejiai.aaf.common.enums.CommonStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user")
@SQLDelete(sql = "UPDATE sys_user SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 200)
    private String password;

    @Column(name = "nickname", length = 100)
    private String nickname;

    /** 0 正常 / 1 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status = CommonStatusEnum.ENABLE.getCode();

    /** 邮箱 */
    @Column(name = "email", length = 200)
    private String email;

    /** 手机号 */
    @Column(name = "phone", length = 20)
    private String phone;

    /** 头像 URL */
    @Column(name = "avatar", length = 500)
    private String avatar;

    /** 邮箱是否验证 */
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    /** 最后登录时间 */
    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    /** 最后登录 IP */
    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;

    /** 注册 IP */
    @Column(name = "register_ip", length = 50)
    private String registerIp;

    /** 注册地址（IP 解析结果，如"广东 深圳市 南山区"） */
    @Column(name = "register_location", length = 100)
    private String registerLocation;

    /** 登录失败次数 */
    @Column(name = "login_fail_count", nullable = false)
    private Integer loginFailCount = 0;

    /** 锁定到期时间 */
    @Column(name = "lock_time")
    private LocalDateTime lockTime;

    /** 注册入口：web / uniapp / api */
    @Column(name = "source_app", length = 32)
    private String sourceApp;

    /** 注册渠道：local / wechat / wechat_mp / dingtalk / github 等 */
    @Column(name = "source_channel", length = 50)
    private String sourceChannel;

    /**
     * 关联联系人 ID（可空）。
     *
     * <p>有真实人对应的用户填此字段；系统账号/机器人账号保持 null。 user 是"能登录的 contact"，contact 是身份本体。
     */
    @Column(name = "contact_id")
    private Long contactId;

    // ==================== 业务方法 ====================

    /** 校验密码是否匹配 */
    public boolean checkPassword(PasswordEncoder encoder, String rawPassword) {
        return encoder.matches(rawPassword, this.password);
    }

    /** 是否启用 */
    public boolean isActive() {
        return CommonStatusEnum.ENABLE.getCode().equals(status);
    }

    /** 修改密码 */
    public void changePassword(PasswordEncoder encoder, String newPassword) {
        this.password = encoder.encode(newPassword);
    }

    /** 是否被锁定 */
    public boolean isLocked() {
        return lockTime != null && lockTime.isAfter(LocalDateTime.now());
    }

    /** 记录登录失败 */
    public void recordLoginFail() {
        this.loginFailCount = (this.loginFailCount == null ? 0 : this.loginFailCount) + 1;
    }

    /** 重置登录失败计数 */
    public void resetLoginFail() {
        this.loginFailCount = 0;
        this.lockTime = null;
    }

    /** 记录登录成功 */
    public void recordLoginSuccess(String ip) {
        this.lastLoginTime = LocalDateTime.now();
        this.lastLoginIp = ip;
        resetLoginFail();
    }
}
