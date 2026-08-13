package com.xuejiai.aaf.module.system.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 当前用户的默认组织与工作区上下文。 */
@Schema(description = "当前用户的默认组织与工作区上下文")
public record DefaultOrgContextVO(
        @Schema(description = "默认组织 ID") String orgId,
        @Schema(description = "默认组织名称") String orgName,
        @Schema(description = "默认工作区 ID") String workspaceId,
        @Schema(description = "默认工作区名称") String workspaceName) {}
