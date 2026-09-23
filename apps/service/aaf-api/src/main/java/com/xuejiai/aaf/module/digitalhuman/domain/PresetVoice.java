package com.xuejiai.aaf.module.digitalhuman.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 预设音色实体
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "preset_voice")
@SQLDelete(sql = "UPDATE preset_voice SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class PresetVoice extends BaseEntity {

    /** 音色名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 性别 */
    @Column(name = "gender", length = 20)
    private String gender;

    /** 示例音频 URL */
    @Column(name = "sample_audio_url", length = 512)
    private String sampleAudioUrl;

    /** 供应商 */
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    /** 外部音色 ID */
    @Column(name = "external_voice_id", nullable = false, length = 100)
    private String externalVoiceId;

    /** 是否启用 */
    @Column(name = "is_active")
    private Boolean isActive = true;

    /** 显示顺序 */
    @Column(name = "display_order")
    private Integer displayOrder = 0;
}
