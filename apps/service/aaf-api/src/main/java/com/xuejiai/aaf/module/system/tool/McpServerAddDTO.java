package com.xuejiai.aaf.module.system.tool;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 添加 MCP Server 请求
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "添加 MCP Server 请求")
public record McpServerAddDTO(
        @NotBlank(message = "服务名称不能为空") @Schema(description = "服务名称") String name,
        @NotBlank(message = "服务地址不能为空") @Schema(description = "服务地址") String url,
        @Schema(description = "描述") String description) {}
