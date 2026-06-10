package com.xuejiai.aaf.module.ai.aigc.project.vo;

import lombok.Data;

/** 分镜响应 VO。 */
@Data
public class AigcShotVO {
    private Long id;
    private Long storyboardId;
    private Integer shotNo;
    private String name;
    private String description;
    private String dialogue;
    private String properties;
}
