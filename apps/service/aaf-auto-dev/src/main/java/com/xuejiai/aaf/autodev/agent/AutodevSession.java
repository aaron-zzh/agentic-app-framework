package com.xuejiai.aaf.autodev.agent;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** AI 自动开发会话实体。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "autodev_session")
@SQLDelete(
        sql =
                "UPDATE autodev_session SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AutodevSession extends BaseEntity {

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "agent_role", nullable = false, length = 50)
    private String agentRole = "kiro_default";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    @Column(name = "user_id", nullable = false)
    private Long userId = 0L;

    /** 会话中涉及的开发文档 ID 列表 */
    @Column(name = "related_doc_ids", columnDefinition = "BIGINT[]")
    private Long[] relatedDocIds;

    /** 会话中涉及的源码文件路径列表 */
    @Column(name = "related_file_paths", columnDefinition = "TEXT[]")
    private String[] relatedFilePaths;
}
