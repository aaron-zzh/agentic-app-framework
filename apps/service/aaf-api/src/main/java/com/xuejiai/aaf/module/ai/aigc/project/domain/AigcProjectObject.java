package com.xuejiai.aaf.module.ai.aigc.project.domain;

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

/** 项目内类型化对象，是 Storyboard、Shot 与交付物的唯一状态源。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_object")
@SQLDelete(
        sql =
                "UPDATE aigc_project_object SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectObject extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "object_type", nullable = false, length = 48)
    private String objectType;

    @Column(name = "object_key", nullable = false, length = 100)
    private String objectKey;

    @Column(name = "blueprint_node_key", length = 100)
    private String blueprintNodeKey;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(length = 300)
    private String title;

    @Column(nullable = false, length = 32)
    private String status = "empty";

    @Column(nullable = false, length = 32)
    private String source = "user";

    @Column(name = "schema_version", length = 32)
    private String schemaVersion;

    @Column(name = "entity_resource", length = 100)
    private String entityResource;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "adopted_version_id")
    private Long adoptedVersionId;

    @Column(length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;
}
