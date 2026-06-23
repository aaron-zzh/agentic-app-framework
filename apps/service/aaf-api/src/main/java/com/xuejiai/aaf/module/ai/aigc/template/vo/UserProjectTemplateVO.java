package com.xuejiai.aaf.module.ai.aigc.template.vo;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

/** 项目模板响应 VO。 */
@Data
public class UserProjectTemplateVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String coverUrl;
    private String category;
    private String projectType;
    private Map<String, Object> templateConfig;
    private Boolean isOfficial;
    private Integer usageCount;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
