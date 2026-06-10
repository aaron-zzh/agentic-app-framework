package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** AIGC 分镜规划。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_storyboard")
@SQLDelete(
        sql =
                "UPDATE aigc_storyboard SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AigcStoryboard extends BaseEntity {

    /** 归属项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 分镜标题 */
    @Column(name = "title", length = 200)
    private String title;

    /** 状态：DRAFT/REVIEWING/APPROVED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    /** 关联脚本文档 ID（doc_document type=aigc_script） */
    @Column(name = "doc_id")
    private Long docId;

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;
}
