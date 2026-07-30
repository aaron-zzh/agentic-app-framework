package com.xuejiai.aaf.module.content.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 内容动作执行绑定。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_execution_binding")
@SQLDelete(
        sql =
                "UPDATE cs_execution_binding SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentExecutionBinding extends BaseEntity {

    @Column(name = "action_key", nullable = false, length = 100)
    private String actionKey;

    @Column(name = "project_type_code", length = 64)
    private String projectTypeCode;

    @Column(name = "domain_extension_code", length = 64)
    private String domainExtensionCode;

    @Column(name = "production_mode", length = 32)
    private String productionMode;

    @Column(name = "channel_code", length = 64)
    private String channelCode;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_ref", nullable = false, length = 200)
    private String targetRef;

    @Column(name = "binding_version", nullable = false, length = 32)
    private String bindingVersion;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Column(name = "confirmation_required", nullable = false)
    private Boolean confirmationRequired = true;

    @Column(name = "estimated_credits", precision = 12, scale = 2)
    private BigDecimal estimatedCredits;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ContentConfigStatusEnum.PUBLISHED.getCode();
}
