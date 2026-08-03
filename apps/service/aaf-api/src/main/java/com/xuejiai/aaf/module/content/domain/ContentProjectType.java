package com.xuejiai.aaf.module.content.domain;

import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 项目类型实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_project_type")
@SQLDelete(
        sql =
                "UPDATE cs_project_type SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentProjectType extends BaseEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "brief_placeholder", length = 500)
    private String briefPlaceholder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_channels", columnDefinition = "jsonb")
    private List<String> defaultChannels = List.of();

    @Column(name = "default_production_mode", length = 32)
    private String defaultProductionMode;

    @Column(name = "quick_entry", nullable = false)
    private Boolean quickEntry = false;

    @Column(name = "builtin", nullable = false)
    private Boolean builtin = false;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "status", nullable = false, length = 32)
    private String status = ContentConfigStatusEnum.DRAFT.getCode();
}
