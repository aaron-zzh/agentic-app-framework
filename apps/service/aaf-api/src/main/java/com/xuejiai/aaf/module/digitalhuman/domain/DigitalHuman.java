package com.xuejiai.aaf.module.digitalhuman.domain;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Type;

import com.xuejiai.aaf.common.model.BaseEntity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 数字人实体
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "digital_human")
@SQLDelete(sql = "UPDATE digital_human SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class DigitalHuman extends BaseEntity {

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 组织 ID */
    @Column(name = "org_id")
    private Long orgId;

    /** 工作空间 ID */
    @Column(name = "workspace_id")
    private Long workspaceId;

    /** 数字人名称 */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** 描述 */
    @Column(name = "description", length = 1000)
    private String description;

    /** 头像图片 URL */
    @Column(name = "avatar_image_url", nullable = false, length = 512)
    private String avatarImageUrl;

    /** 标签 */
    @Type(JsonType.class)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    /** 外观配置 */
    @Type(JsonType.class)
    @Column(name = "appearance", columnDefinition = "jsonb")
    private AppearanceConfig appearance;

    /** 音色配置 */
    @Type(JsonType.class)
    @Column(name = "voice_config", nullable = false, columnDefinition = "jsonb")
    private VoiceConfig voiceConfig;

    /** 模型配置 */
    @Type(JsonType.class)
    @Column(name = "model_config", columnDefinition = "jsonb")
    private ModelConfig modelConfig;

    /** 关联助理 ID */
    @Column(name = "assistant_id")
    private Long assistantId;

    /** 是否同步助理的模型配置 */
    @Column(name = "sync_model_config")
    private Boolean syncModelConfig = false;

    /** 生成的视频数量 */
    @Column(name = "video_count")
    private Integer videoCount = 0;

    /** 视频总时长（秒） */
    @Column(name = "total_duration")
    private Integer totalDuration = 0;

    /** 消耗的积分数 */
    @Column(name = "credits_consumed")
    private Integer creditsConsumed = 0;

    /** 最后使用时间 */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 状态: active, archived */
    @Column(name = "status", length = 32)
    private String status = "active";

    /** 生成来源 */
    @Column(name = "generation_source", length = 50)
    private String generationSource;

    /** AIGC 项目 ID */
    @Column(name = "aigc_project_id")
    private Long aigcProjectId;

    /**
     * 外观配置
     */
    @Data
    public static class AppearanceConfig {
        /** 年龄段 */
        private String age;

        /** 性别 */
        private String gender;

        /** 风格 */
        private String style;

        /** 生成提示词 */
        private String prompt;
    }

    /**
     * 音色配置
     */
    @Data
    public static class VoiceConfig {
        /** 供应商 */
        private String provider;

        /** 预设音色 ID */
        private Long presetVoiceId;

        /** 音色名称 */
        private String voiceName;

        /** 语速 */
        private Integer speechRate;

        /** 音调 */
        private Integer pitchRate;

        /** 音量 */
        private Integer volume;
    }

    /**
     * 模型配置
     */
    @Data
    public static class ModelConfig {
        /** 供应商 */
        private String provider;

        /** 模型 */
        private String model;

        /** 温度 */
        private Double temperature;

        /** 最大 token 数 */
        private Integer maxTokens;

        /** 系统提示词 */
        private String systemPrompt;
    }
}
