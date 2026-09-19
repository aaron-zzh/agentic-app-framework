package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentContextMode;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectDocumentReferenceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 项目自有文档身份或外部文档精确版本的引用。 */
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

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 32)
    private AigcProjectDocumentReferenceType referenceType;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "field_key", length = 64)
    private String fieldKey;

    @Column(name = "instance_no", nullable = false)
    private Integer instanceNo = 0;

    @Column(name = "selected_document_version_id")
    private Long selectedDocumentVersionId;

    @Column(nullable = false, length = 32)
    private String source = "user";

    @Column(name = "context_note", length = 500)
    private String contextNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_mode", nullable = false, length = 16)
    private AigcProjectDocumentContextMode contextMode = AigcProjectDocumentContextMode.AUTO;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
