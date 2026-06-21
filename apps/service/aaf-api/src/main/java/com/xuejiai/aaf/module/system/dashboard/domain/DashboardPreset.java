package com.xuejiai.aaf.module.system.dashboard.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 仪表盘预设模板实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_dashboard_preset")
@SQLDelete(
        sql =
                "UPDATE sys_dashboard_preset SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class DashboardPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preset_key", nullable = false, length = 64)
    private String presetKey;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "admin_only", nullable = false)
    private Boolean adminOnly = false;

    @Column(name = "refresh_interval", nullable = false)
    private Integer refreshInterval = 300;

    /** Widget 布局配置（JSON 数组，格式同 DashboardWidgetVO） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "widgets", columnDefinition = "jsonb")
    private String widgets;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "status", nullable = false)
    private Short status = 0;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime = LocalDateTime.now();

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;
}
