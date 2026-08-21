package com.xuejiai.aaf.framework.engine.prompt;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 提示词不可变内容版本。 */
@Getter
@Setter
@Entity
@Table(name = "ai_prompt_template_version")
@SQLDelete(
        sql =
                "UPDATE ai_prompt_template_version SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class PromptTemplateVersion extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "prompt_template_id", nullable = false)
    private PromptTemplate template;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PromptVersionStatus status = PromptVersionStatus.DRAFT;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "negative_prompt", columnDefinition = "TEXT")
    private String negativePrompt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String variables = "[]";

    @Column(length = 100)
    private String model;

    private Integer width;
    private Integer height;
    private Integer steps;
    private Long seed;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "change_summary", length = 512)
    private String changeSummary;
}
