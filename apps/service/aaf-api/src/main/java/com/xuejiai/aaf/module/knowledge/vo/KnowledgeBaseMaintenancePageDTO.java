package com.xuejiai.aaf.module.knowledge.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识库后台运维分页查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识库后台运维分页查询")
public class KnowledgeBaseMaintenancePageDTO extends PageParam {

    @Schema(description = "知识库名称，模糊匹配")
    private String name;

    @Schema(description = "所属组织 ID")
    private Long orgId;

    @Schema(description = "所属工作空间 ID")
    private Long workspaceId;

    @Schema(description = "数据归属用户 ID")
    private Long ownerId;

    @Schema(description = "可见范围：PRIVATE / ORG / SYSTEM_PUBLIC")
    private String visibility;

    @Schema(description = "状态：0 启用，1 禁用")
    private Integer status;
}
