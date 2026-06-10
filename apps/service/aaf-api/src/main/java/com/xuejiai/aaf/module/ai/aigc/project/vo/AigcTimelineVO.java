package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 时间轴响应 VO。 */
@Data
public class AigcTimelineVO {
    private Long id;
    private Long projectId;
    private Long storyboardId;
    private String title;
    private String status;
    private Long durationMs;
    private Short fps;
    private String resolution;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
