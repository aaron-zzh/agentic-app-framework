package com.xuejiai.aaf.module.system.profile.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 画像维度定义。 */
@Getter
@Setter
@Entity
@Table(name = "sys_profile_dimension")
public class ProfileDimension extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "group_code", nullable = false, length = 32)
    private String groupCode;

    @Column(name = "value_type", nullable = false, length = 32)
    private String valueType;

    @Column(name = "enum_options", columnDefinition = "jsonb")
    private String enumOptions;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "required", nullable = false)
    private Boolean required = false;

    @Column(name = "searchable", nullable = false)
    private Boolean searchable = false;

    @Column(name = "ai_visible", nullable = false)
    private Boolean aiVisible = true;

    @Column(name = "status", nullable = false)
    private Integer status = 0;
}
