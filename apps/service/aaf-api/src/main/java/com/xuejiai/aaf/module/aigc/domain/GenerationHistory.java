package com.xuejiai.aaf.module.aigc.domain;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 生成历史记录。 */
@Getter
@Setter
@Entity
@Table(name = "generation_history")
public class GenerationHistory extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MediaAssetType type;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "params", columnDefinition = "TEXT")
    private String params;

    @Column(name = "result_url", length = 500)
    private String resultUrl;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "session_id")
    private Long sessionId;
}
