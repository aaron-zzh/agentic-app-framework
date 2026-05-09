package com.xuejiai.aaf.module.system.domain;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.xuejiai.aaf.common.enums.CommonStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 系统用户。 */
@Getter
@Setter
@Entity
@Table(name = "sys_user")
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
}
