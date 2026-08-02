package com.xuejiai.aaf.module.knowledge.domain;

import java.util.UUID;

import org.hibernate.annotations.Formula;
import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.CommonStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库实体。
 *
 * <p>一个知识库包含多个 {@link KnowledgeDocument}，通过向量化和图谱构建支持语义检索。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "ai_knowledge_base")
@SQLDelete(
        sql =
                "UPDATE ai_knowledge_base SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE"
                        + " id = ?")
public class KnowledgeBase extends BaseEntity {

    @Column(name = "stable_id", nullable = false, unique = true)
    private UUID stableId = UUID.randomUUID();

    @Column(name = "visibility", nullable = false, length = 24)
    private String visibility = "PRIVATE";

    @Column(name = "scope_code", length = 128)
    private String scopeCode;

    /** 知识库名称 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 描述 */
    @Column(name = "description", length = 1000)
    private String description;

    /** 向量模型名称 */
    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    /** 分块策略 */
    @Column(name = "chunk_strategy", length = 50)
    private String chunkStrategy = "recursive";

    /** 分块大小 */
    @Column(name = "chunk_size")
    private Integer chunkSize = 512;

    /** 分块重叠 */
    @Column(name = "chunk_overlap")
    private Integer chunkOverlap = 64;

    /**
     * 状态
     *
     * <p>枚举 {@link com.xuejiai.aaf.common.enums.CommonStatusEnum}
     */
    @Column(name = "status", nullable = false)
    private Integer status = CommonStatusEnum.ENABLE.getCode();

    /** 未删除文档数量，由数据库关联统计，不新增冗余列。 */
    @Formula(
            "(SELECT COUNT(*) FROM ai_knowledge_document document WHERE document.knowledge_base_id = id AND document.deleted = false)")
    private Long documentCount;

    /**
     * 是否自动注入。
     *
     * <p>true = Agent 启动时自动加载该知识库的 top 片段注入系统提示词（最多 3000 字符）； false = 仅支持 search_kb 工具检索。
     */
    @Column(name = "auto_inject", nullable = false)
    private Boolean autoInject = false;
}
