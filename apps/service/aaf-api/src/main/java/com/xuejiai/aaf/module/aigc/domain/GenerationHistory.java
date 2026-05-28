package com.xuejiai.aaf.module.aigc.domain;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 生成历史记录。
 *
 * <p>记录每次 AI 生成的完整信息，关联 {@link MediaAsset}。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "generation_history")
public class GenerationHistory extends BaseEntity {

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 生成类型，枚举 {@link MediaAssetType} */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MediaAssetType type;

    /** 生成提示词 */
    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    /** 使用的模型名称 */
    @Column(name = "model", length = 100)
    private String model;

    /** 生成参数（JSON） */
    @Column(name = "params", columnDefinition = "TEXT")
    private String params;

    /** 生成结果 URL */
    @Column(name = "result_url", length = 500)
    private String resultUrl;

    /** 关联的素材 ID，关联 {@link MediaAsset} */
    @Column(name = "asset_id")
    private Long assetId;

    /** 会话 ID */
    @Column(name = "session_id")
    private Long sessionId;
}
