package com.xuejiai.aaf.module.ai.aigc.voice.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 声音复刻记录实体。
 *
 * <p>存储用户通过 qwen-voice-enrollment 创建的克隆音色元数据。 voice 字段由百炼平台生成并返回，可直接用于 OmniRealtimeService 的 voice
 * 参数。
 *
 * @author Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "ai_cloned_voice")
@SQLDelete(
        sql =
                "UPDATE ai_cloned_voice SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class AiClonedVoice extends BaseEntity {

    /** 百炼平台 voice 名称，如 qwen-omni-vc-myvoice-20250812105009-xxx */
    @Column(name = "voice", nullable = false, length = 200)
    private String voice;

    /** 用户设置的音色别名（仅字母/数字/下划线，≤16字符） */
    @Column(name = "preferred_name", nullable = false, length = 64)
    private String preferredName;

    /** 绑定的全模态模型，对话时必须使用同一模型 */
    @Column(name = "target_model", nullable = false, length = 100)
    private String targetModel;

    /** 复刻原始音频的 media_asset.id，可为空 */
    @Column(name = "source_asset_id")
    private Long sourceAssetId;

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 示例音频 OSS URL，克隆完成后自动生成 */
    @Column(name = "sample_audio_url", length = 1000)
    private String sampleAudioUrl;
}
