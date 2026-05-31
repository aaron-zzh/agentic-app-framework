package com.xuejiai.aaf.module.developer.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 开发者子代理配置，用于受控分销层级。 */
@Getter
@Setter
@Entity
@Table(name = "developer_proxy")
@SQLDelete(
        sql =
                "UPDATE developer_proxy SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class DeveloperProxy extends BaseEntity {

    @Column(name = "parent_developer_id", nullable = false)
    private Long parentDeveloperId;

    @Column(name = "child_developer_id", nullable = false)
    private Long childDeveloperId;

    @Column(name = "proxy_depth", nullable = false)
    private Integer proxyDepth = 1;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "token_limit")
    private Long tokenLimit;

    @Column(name = "allow_sub_proxy", nullable = false)
    private Boolean allowSubProxy = false;
}
