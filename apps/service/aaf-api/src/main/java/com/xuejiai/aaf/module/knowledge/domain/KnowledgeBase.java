package com.xuejiai.aaf.module.knowledge.domain;

import java.time.LocalDateTime;
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

    /**
     * 知识库说明：用户填写，系统永不修改。
     *
     * <p>写定位：面向谁、回答哪类问题、不适用哪些问题。
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * 涵盖主题：系统根据已入库文档自动生成，只读。
     *
     * <p>写资料实际覆盖的主题，形如“涵盖主题：饮食管理、血糖监测、低血糖处理。概述：……”。由汇总任务通过 JDBC 写入，JPA 不插入、不更新。
     */
    @Column(name = "ai_summary", length = 1000, insertable = false, updatable = false)
    private String aiSummary;

    /** 涵盖主题最近生成时间，只读。 */
    @Column(name = "ai_summary_updated_at", insertable = false, updatable = false)
    private LocalDateTime aiSummaryUpdatedAt;

    /** 创建时记录所用的全局向量模型 modelId，只读；向量模型全局唯一，不随知识库切换。 */
    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    /** 分块策略：auto 使用默认块大小与重叠，semantic 使用本库自定义块大小与重叠；两者都先按章节拆分再按语义边界切分。 */
    @Column(name = "chunk_strategy", length = 50)
    private String chunkStrategy = "auto";

    /** 分块大小 */
    @Column(name = "chunk_size")
    private Integer chunkSize = 800;

    /** 分块重叠 */
    @Column(name = "chunk_overlap")
    private Integer chunkOverlap = 50;

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
