package com.xuejiai.aaf.module.ai.aigc.project.resource.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 项目资源关联 VO。 */
@Data
public class UserProjectResourceVO {
    private Long id;
    private Long projectId;
    private String resourceType;
    private Long resourceId;
    private String role;
    private Integer sortOrder;

    /** 联表回填，便于前端展示 */
    private String resourceName;

    private String resourceCoverUrl;
    private LocalDateTime createTime;
}
