package com.xuejiai.aaf.module.system.dashboard.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 仪表盘组件。 */
@Getter
@Setter
@Entity
@Table(name = "sys_dashboard_widget")
@SQLDelete(
        sql =
                "UPDATE sys_dashboard_widget SET deleted = true, delete_time = CURRENT_TIMESTAMP"
                        + " WHERE id = ?")
public class DashboardWidget extends BaseEntity {

    /** 所属仪表盘 ID */
    @Column(name = "dashboard_id", nullable = false)
    private Long dashboardId;

    /** 组件类型：counter/chart/list/progress/shortcut/custom */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 组件标题 */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /** 位置信息（x/y/w/h） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "position", nullable = false, columnDefinition = "jsonb")
    private String position;

    /** 组件配置（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private String config;

    /** 排序序号 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
