package com.xuejiai.aaf.module.ai.digitalhuman.domain;

import com.xuejiai.aaf.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/**
 * 预设音色库
 *
 * <p>存储平台提供的预设音色（阿里云 TTS），用户创建数字人时选择。
 * 包含音色名称、描述、试听样本、第三方 API 配置等。
 */
@Getter
@Setter
@Entity
@Table(name = "preset_voice")
@SQLDelete(sql = "UPDATE preset_voice SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class PresetVoice extends BaseEntity {

    /** 音色名称（如：艾佳） */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 音色描述（适用场景） */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 性别：male-男, female-女 */
    @Column(name = "gender", length = 16)
    private String gender;

    /** 试听音频样本 URL */
    @Column(name = "sample_audio_url", length = 512)
    private String sampleAudioUrl;

    /** 提供商：aliyun */
    @Column(name = "provider", nullable = false, length = 64)
    private String provider = "aliyun";

    /** 第三方音色 ID（阿里云音色 ID） */
    @Column(name = "external_voice_id", nullable = false, length = 128)
    private String externalVoiceId;

    /** 是否启用 */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** 显示顺序 */
    @Column(name = "display_order")
    private Integer displayOrder = 0;
}
