package com.xuejiai.aaf.module.ai.aigc.workflow.vo;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

/** 用户工作流模板 VO。 */
@Data
public class UserWorkflowTemplateVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String coverUrl;
    private String category;
    private Map<String, Object> templateConfig;
    private Boolean isOfficial;
    private Integer usageCount;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
