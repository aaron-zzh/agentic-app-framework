package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 分镜规划响应 VO。 */
@Data
public class AigcStoryboardVO {
    private Long id;
    private Long projectId;
    private String title;
    private String status;
    private Long docId;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
