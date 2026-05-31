package com.xuejiai.aaf.module.developer.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 开发者账户：承载框架开发者授权、托管网关和子代理资格。 */
@Getter
@Setter
@Entity
@Table(name = "developer_account")
@SQLDelete(
        sql =
                "UPDATE developer_account SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class DeveloperAccount extends BaseEntity {

    @Column(name = "developer_code", nullable = false, unique = true, length = 64)
    private String developerCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "license_id", length = 120)
    private String licenseId;

    @Column(name = "license_tier", length = 32)
    private String licenseTier = "FREE";

    @Column(name = "allow_managed_gateway", nullable = false)
    private Boolean allowManagedGateway = false;

    @Column(name = "allow_sub_proxy", nullable = false)
    private Boolean allowSubProxy = false;

    @Column(name = "max_proxy_depth", nullable = false)
    private Integer maxProxyDepth = 0;
}
