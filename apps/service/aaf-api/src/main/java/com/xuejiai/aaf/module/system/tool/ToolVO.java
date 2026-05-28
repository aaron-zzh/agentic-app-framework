package com.xuejiai.aaf.module.system.tool;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工具视图对象
 *
 * @author AaronZZH & Kiro
 */
public record ToolVO(
        @Schema(description = "工具名称", example = "web_search") String name,
        @Schema(description = "工具描述", example = "搜索互联网信息") String description,
        @Schema(description = "工具来源", example = "LOCAL") String source,
        @Schema(description = "参数 JSON Schema") String parametersSchema) {}
