package com.xuejiai.aaf.module.elder.rehab.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.elder.RehabGuideStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 康复指导索引；正文由关联的知识文档承载。 */
@Getter
@Setter
@Entity
@Table(name = "elder_rehab_guide")
@SQLDelete(
        sql =
                "UPDATE elder_rehab_guide SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class RehabGuide extends BaseEntity {

    /** 标题。 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 康复分类。 */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /** 内容摘要。 */
    @Column(name = "summary", nullable = false, length = 1000)
    private String summary;

    /** 适用人群说明。 */
    @Column(name = "applicable_audience", nullable = false, length = 1000)
    private String applicableAudience;

    /** 关联知识文档主键。 */
    @CrudReference(targetResource = "knowledge.knowledge-document")
    @Column(name = "knowledge_document_id", nullable = false)
    private Long knowledgeDocumentId;

    /** 发布状态。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = RehabGuideStatusEnum.DRAFT.getCode();
}
