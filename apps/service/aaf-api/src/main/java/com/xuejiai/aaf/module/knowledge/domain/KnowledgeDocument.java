package com.xuejiai.aaf.module.knowledge.domain;

import java.util.UUID;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库文档实体。
 *
 * <p>关联 {@link KnowledgeBase}，上传后经分块、向量化入库。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "ai_knowledge_document")
@SQLDelete(
        sql =
                "UPDATE ai_knowledge_document SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE"
                        + " id = ?")
public class KnowledgeDocument extends BaseEntity {

    @Column(name = "stable_id", nullable = false, unique = true)
    private UUID stableId = UUID.randomUUID();

    @Column(name = "source_document_id")
    private Long sourceDocumentId;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType = "FILE";

    @Column(name = "source_key", length = 1000)
    private String sourceKey;

    @Column(name = "source_uri", length = 2000)
    private String sourceUri;

    @Column(name = "active_run_id")
    private UUID activeRunId;

    /**
     * 所属知识库 ID
     *
     * <p>关联 {@link KnowledgeBase#getId()}
     */
    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    /** 文档标题 */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /** 文件路径 */
    @Column(name = "file_path", length = 1000)
    private String filePath;

    /** 文件类型 */
    @Column(name = "file_type", length = 50)
    private String fileType;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** 内容哈希 */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** 状态（0=待处理 1=处理中 2=已完成 3=失败） */
    @Column(name = "status")
    private Integer status = 0;

    /** 错误信息 */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** 分块数量 */
    @Column(name = "chunk_count")
    private Integer chunkCount = 0;
}
