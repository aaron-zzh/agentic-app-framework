package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;

import lombok.Data;

/** 时间轴轨道 VO（含片段列表）。 */
@Data
public class AigcTrackVO {
    private Long id;
    private Long timelineId;

    /** 轨道类型：VIDEO/AUDIO/SUBTITLE/STICKER */
    private String type;

    private Integer sortOrder;
    private Boolean muted;
    private Boolean locked;

    /** 轨道下的所有片段 */
    private List<AigcClipVO> clips;
}
