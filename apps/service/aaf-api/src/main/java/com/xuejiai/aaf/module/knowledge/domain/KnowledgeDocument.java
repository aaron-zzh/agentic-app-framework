package com.xuejiai.aaf.module.knowledge.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 知识库文档。 */
@Getter
@Setter
@Entity
@Table(name = "knowledge_document")
@SQLDelete(
        sql =
                "UPDATE knowledge_document SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE"
                        + " id = ?")
public class KnowledgeDocument extends BaseEntity {

    /** 所属知识库 ID */
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

    /** 状态：0=待处理，1=处理中，2=已完成，3=失败 */
    @Column(name = "status", nullable = false)
    private Integer status = 0;

    /** 错误信息 */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** 分块数量 */
    @Column(name = "chunk_count")
    private Integer chunkCount = 0;
}
