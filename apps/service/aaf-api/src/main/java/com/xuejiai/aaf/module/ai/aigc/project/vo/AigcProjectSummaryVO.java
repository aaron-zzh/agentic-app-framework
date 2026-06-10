package com.xuejiai.aaf.module.ai.aigc.project.vo;

import lombok.Data;

/** 创作项目概览统计 VO。 */
@Data
public class AigcProjectSummaryVO {
    private Long id;
    private String name;

    /** 分镜板数量 */
    private int storyboardCount;

    /** 时间轴数量 */
    private int timelineCount;

    /** 内容产出数量 */
    private int contentCount;

    /** 素材数量（media_element 下） */
    private int assetCount;
}
