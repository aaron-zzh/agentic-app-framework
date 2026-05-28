package com.xuejiai.aaf.module.knowledge.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库段落实体。
 *
 * <p>文档分块后的最小检索单元，支持向量语义搜索。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "knowledge_segment")
@SQLDelete(
        sql =
                "UPDATE knowledge_segment SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE"
                        + " id = ?")
public class KnowledgeSegment extends BaseEntity {

    /** 所属文档 ID */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 所属知识库 ID */
    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    /** 段落内容 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 段落在文档中的位置（从 0 开始） */
    @Column(name = "position", nullable = false)
    private Integer position;

    /** 字数 */
    @Column(name = "word_count")
    private Integer wordCount;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /** 向量嵌入（PgVector） */
    @Column(name = "embedding", columnDefinition = "vector")
    private String embedding;
}
