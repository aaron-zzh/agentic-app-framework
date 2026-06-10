package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** AIGC 内容产出（最终发布内容）。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_content")
@SQLDelete(
        sql =
                "UPDATE aigc_content SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AigcContent extends BaseEntity {

    /** 归属项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 内容类型：SHORT_VIDEO/IMAGE_POST/RICH_TEXT */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    /** 内容标题 */
    @Column(name = "title", length = 200)
    private String title;

    /** 委托 doc_document 存储正文（M2O） */
    @Column(name = "doc_id")
    private Long docId;

    /** 关联素材 ID 列表（冗余，详细关系见 aigc_content_asset） */
    @Column(name = "asset_ids", columnDefinition = "JSONB")
    private String assetIds;

    /** 发布目标平台：WECHAT/DOUYIN/XIAOHONGSHU/BILIBILI */
    @Column(name = "platform", length = 50)
    private String platform;

    /** 发布状态：DRAFT/REVIEWING/PUBLISHED/FAILED */
    @Column(name = "publish_status", nullable = false, length = 20)
    private String publishStatus = "DRAFT";

    /** 发布时间 */
    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;
}
