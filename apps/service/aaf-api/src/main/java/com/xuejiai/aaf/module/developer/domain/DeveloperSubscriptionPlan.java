package com.xuejiai.aaf.module.developer.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 开发者订阅套餐。 */
@Getter
@Setter
@Entity
@Table(name = "developer_subscription_plan")
@SQLDelete(
        sql =
                "UPDATE developer_subscription_plan SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class DeveloperSubscriptionPlan extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "included_tokens", nullable = false)
    private Long includedTokens = 0L;

    @Column(name = "allow_managed_gateway", nullable = false)
    private Boolean allowManagedGateway = false;

    @Column(name = "allow_sub_proxy", nullable = false)
    private Boolean allowSubProxy = false;

    @Column(name = "max_proxy_depth", nullable = false)
    private Integer maxProxyDepth = 0;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ENABLED";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;
}
