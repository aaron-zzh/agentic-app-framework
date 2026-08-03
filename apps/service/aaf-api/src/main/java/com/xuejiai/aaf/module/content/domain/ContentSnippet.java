package com.xuejiai.aaf.module.content.domain;

import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 创作片段实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "cs_snippet")
@SQLDelete(
        sql = "UPDATE cs_snippet SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContentSnippet extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_image_urls", columnDefinition = "jsonb")
    private List<String> referenceImageUrls = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variable_slots", columnDefinition = "jsonb")
    private Map<String, Object> variableSlots;

    @Column(name = "project_type_code", length = 64)
    private String projectTypeCode;

    @Column(name = "brand_profile_id")
    private Long brandProfileId;

    @Column(name = "use_count", nullable = false)
    private Integer useCount = 0;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;
}
