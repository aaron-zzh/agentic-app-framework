package com.xuejiai.aaf.module.ai.aigc.project.vo;

import lombok.Data;

/** 时间轴片段 VO。 */
@Data
public class AigcClipVO {
    private Long id;
    private Long trackId;
    private Long assetId;
    private Long shotId;

    /** 在轨道上的起始位置（毫秒） */
    private Long positionMs;

    /** 素材入点（毫秒） */
    private Long inMs;

    /** 素材出点（毫秒） */
    private Long outMs;

    /** 扩展属性 JSON */
    private String properties;
}
