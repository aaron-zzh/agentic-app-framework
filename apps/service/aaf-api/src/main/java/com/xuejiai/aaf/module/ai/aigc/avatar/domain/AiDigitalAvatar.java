package com.xuejiai.aaf.module.ai.aigc.avatar.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 数字人形象——存储用户上传的形象图片及检测状态。 */
@Getter
@Setter
@Entity
@Table(name = "ai_digital_avatar")
public class AiDigitalAvatar extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "source_asset_id")
    private Long sourceAssetId;

    /** 图片合规检测状态：PENDING / PASSED / FAILED */
    @Column(name = "detect_status", nullable = false, length = 20)
    private String detectStatus;

    @Column(name = "detect_reason", length = 500)
    private String detectReason;

    @Column(name = "default_voice", length = 200)
    private String defaultVoice;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
