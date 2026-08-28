package com.xuejiai.aaf.framework.engine.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** SkillVersion 挂载的参考文档；对应 {@code ai_skill_reference}。 */
@Getter
@Setter
@Entity
@Table(name = "ai_skill_reference")
public class SkillReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "reference_key", nullable = false, length = 100)
    private String referenceKey;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "document_version_id", nullable = false)
    private Long documentVersionId;

    @Column(length = 512)
    private String selector;

    @Column(name = "load_mode", nullable = false, length = 16)
    private String loadMode = "ON_DEMAND";

    @Column(name = "load_when", nullable = false, length = 512)
    private String loadWhen;

    @Column(nullable = false)
    private Boolean required = false;

    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens = 1024;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    @Column(name = "content_hash", length = 128)
    private String contentHash;
}
