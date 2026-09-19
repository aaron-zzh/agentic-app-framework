package com.xuejiai.aaf.module.document.domain;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.document.api.DocumentVersionSemanticMetadataApi.SemanticTopic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 与不可变正文版本一一对应、可独立重建和人工覆盖的语义元数据。 */
@Getter
@Setter
@Entity
@Table(name = "doc_document_version_semantic_metadata")
public class DocumentVersionSemanticMetadata extends BaseEntity {

    @Column(name = "document_version_id", nullable = false, unique = true)
    private Long documentVersionId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<SemanticTopic> topics = List.of();

    @Column(length = 32)
    private String language;

    @Column(name = "generation_status", nullable = false, length = 16)
    private String generationStatus = "PENDING";

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType = "AI";

    @Column(name = "model_ref", length = 128)
    private String modelRef;

    @Column(name = "manual_override", nullable = false)
    private Boolean manualOverride = false;

    @Column(name = "generated_time")
    private LocalDateTime generatedTime;
}
