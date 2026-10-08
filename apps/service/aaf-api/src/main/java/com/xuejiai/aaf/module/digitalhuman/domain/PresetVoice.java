package com.xuejiai.aaf.module.digitalhuman.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 数字人预设音色实体
 *
 * <p>全局共享的音色库，{@code org_id} 为空，标注 {@link OrgIgnore} 以免组织过滤器过滤掉全部记录。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@OrgIgnore
@Entity
@Table(name = "digital_human_preset_voice")
@SQLDelete(
        sql =
                "UPDATE digital_human_preset_voice SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class PresetVoice extends BaseEntity {

    /** 音色名称 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 描述 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 性别 */
    @Column(name = "gender", length = 16)
    private String gender;

    /** 示例音频 URL */
    @Column(name = "sample_audio_url", length = 512)
    private String sampleAudioUrl;

    /** 供应商 */
    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    /** 外部音色 ID */
    @Column(name = "external_voice_id", nullable = false, length = 128)
    private String externalVoiceId;

    /** 是否启用 */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** 显示顺序 */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
