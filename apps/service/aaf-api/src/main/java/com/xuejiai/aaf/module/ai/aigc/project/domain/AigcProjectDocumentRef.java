package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目对外部文档精确版本的引用。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_document_ref")
@SQLDelete(
        sql =
                "UPDATE aigc_project_document_ref SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectDocumentRef extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "object_id")
    private Long objectId;

    @Column(name = "document_version_id", nullable = false)
    private Long documentVersionId;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
