package com.xuejiai.aaf.module.ai.aigc.configuration.domain;

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

/** 可复用轻量创作片段，媒体引用固定到不可变版本。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_snippet")
@SQLDelete(
        sql =
                "UPDATE aigc_snippet SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcSnippet extends BaseEntity {

    @Column(name = "builtin_code", length = 64)
    private String builtinCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_media_version_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> referenceMediaVersionIds = List.of();

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
