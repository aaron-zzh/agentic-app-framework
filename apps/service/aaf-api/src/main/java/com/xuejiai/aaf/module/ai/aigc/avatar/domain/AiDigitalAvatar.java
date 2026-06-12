package com.xuejiai.aaf.module.ai.aigc.avatar.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 数字人形象实体。
 *
 * <p>存储用户上传的形象图片及 wan2.2-s2v-detect 检测状态。 检测通过后可提交视频生成任务（走 aigc_task，type=AVATAR_VIDEO）。
 *
 * @author Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "ai_digital_avatar")
@SQLDelete(
        sql =
                "UPDATE ai_digital_avatar SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AiDigitalAvatar extends BaseEntity {

    /** 形象名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 形象图片公网 URL */
    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    /** 图片来源素材 ID（media_asset.id），可为空 */
    @Column(name = "source_asset_id")
    private Long sourceAssetId;

    /** 检测状态：PENDING / PASSED / FAILED */
    @Column(name = "detect_status", nullable = false, length = 20)
    private String detectStatus = "PENDING";

    /** 检测失败原因 */
    @Column(name = "detect_reason", length = 500)
    private String detectReason;

    /** 默认绑定的克隆音色（ai_cloned_voice.voice） */
    @Column(name = "default_voice", length = 200)
    private String defaultVoice;

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;
}
