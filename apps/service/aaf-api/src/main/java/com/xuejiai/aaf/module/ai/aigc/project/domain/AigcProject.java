package com.xuejiai.aaf.module.ai.aigc.project.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** AIGC 创作项目。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project")
@SQLDelete(
        sql =
                "UPDATE aigc_project SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProject extends BaseEntity {

    /** 项目名称 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 封面图 URL */
    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    /** 项目描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 项目类型：VIDEO_DRAMA/IMAGE_POST/SHORT_VIDEO/MIXED */
    @Column(name = "type", nullable = false, length = 30)
    private String type = "MIXED";

    /** 项目状态：DRAFT/IN_PROGRESS/COMPLETED/ARCHIVED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 项目级提示词——生成时直接使用，文档为可选增强 */
    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;
}
