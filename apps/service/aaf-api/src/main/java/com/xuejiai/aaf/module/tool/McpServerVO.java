package com.xuejiai.aaf.module.tool;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * MCP Server 视图对象
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "MCP Server 信息")
public record McpServerVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "服务名称") String name,
        @Schema(description = "服务地址") String url,
        @Schema(description = "描述") String description) {}
