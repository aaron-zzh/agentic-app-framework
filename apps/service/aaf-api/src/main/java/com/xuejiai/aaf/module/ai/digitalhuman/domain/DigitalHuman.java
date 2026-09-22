package com.xuejiai.aaf.module.ai.digitalhuman.domain;

import com.xuejiai.aaf.common.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * 数字人实体
 *
 * <p>数字人是具备形象、音色、语言能力的虚拟角色，用于视频生成、内容播报等场景。
 * 支持与助理双向关联，可复用助理的大模型配置。
 */
@Getter
@Setter
@Entity
@Table(name = "digital_human")
@SQLDelete(sql = "UPDATE digital_human SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class DigitalHuman extends BaseEntity {

    /** 数字人名称 */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** 数字人描述 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 标签（营销、客服、教育等） */
    @Type(JsonType.class)
    @Column(name = "tags", columnDefinition = "jsonb")
    private String[] tags;

    /** 数字人头像图片 URL */
    @Column(name = "avatar_image_url", nullable = false, length = 512)
    private String avatarImageUrl;

    /** 形象信息（年龄、性别、风格、提示词等） */
    @Type(JsonType.class)
    @Column(name = "appearance", columnDefinition = "jsonb")
    private AppearanceConfig appearance;

    /** 音色配置（阿里云预设音色） */
    @Type(JsonType.class)
    @Column(name = "voice_config", nullable = false, columnDefinition = "jsonb")
    private VoiceConfig voiceConfig;

    /** 大模型配置 */
    @Type(JsonType.class)
    @Column(name = "model_config", columnDefinition = "jsonb")
    private ModelConfig modelConfig;

    /** 关联的助理 ID（双向绑定） */
    @Column(name = "assistant_id")
    private Long assistantId;

    /** 是否同步助理的模型配置 */
    @Column(name = "sync_model_config")
    private Boolean syncModelConfig = false;

    /** 生成视频数量 */
    @Column(name = "video_count")
    private Integer videoCount = 0;

    /** 累计视频时长（秒） */
    @Column(name = "total_duration")
    private Integer totalDuration = 0;

    /** 累计消耗积分 */
    @Column(name = "credits_consumed")
    private Integer creditsConsumed = 0;

    /** 最后使用时间 */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 状态：active-激活, archived-归档 */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "active";

    /** 生成来源：aigc_project */
    @Column(name = "generation_source", length = 32)
    private String generationSource;

    /** 关联的 AIGC 项目 ID */
    @Column(name = "aigc_project_id")
    private Long aigcProjectId;

    /**
     * 形象配置
     */
    @Getter
    @Setter
    public static class AppearanceConfig {
        private Integer age;
        private String gender;
        private String style;
        private String prompt;
    }

    /**
     * 音色配置
     */
    @Getter
    @Setter
    public static class VoiceConfig {
        private String provider;        // aliyun
        private String presetVoiceId;   // aijia, aitong, xiaoyun, aicheng, aiwei
        private String voiceName;       // 艾佳, 艾彤, 小云, 艾诚, 艾伟
        private Integer speechRate;     // 语速 [-500, 500]
        private Integer pitchRate;      // 音调 [-500, 500]
        private Integer volume;         // 音量 [0, 100]
    }

    /**
     * 模型配置
     */
    @Getter
    @Setter
    public static class ModelConfig {
        private String provider;        // anthropic, openai
        private String model;           // claude-sonnet-5, gpt-4
        private Double temperature;     // [0.0, 1.0]
        private Integer maxTokens;
        private String systemPrompt;
    }
}
