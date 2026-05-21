package com.xuejiai.aaf.module.system.dashboard.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 页面定义（PageEngine 配置驱动的营销页/落地页）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_page_def")
@SQLDelete(
        sql =
                "UPDATE sys_page_def SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class PageDef extends BaseEntity {

    /** 页面路径标识（唯一） */
    @Column(name = "slug", nullable = false, length = 200)
    private String slug;

    /** 页面标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 页面配置（JSONB，含 sections/theme/metadata） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private String config;

    /** 状态：draft/published */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "draft";

    /** 最近发布时间 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
