package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 创作项目响应 VO。 */
@Data
public class AigcProjectVO {
    private Long id;
    private String name;
    private String coverUrl;
    private String description;
    private String type;
    private String status;
    private Long userId;
    private String prompt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
