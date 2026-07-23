package com.xuejiai.aaf.module.system.entity.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

/**
 * 实体定义响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "实体定义信息")
public record EntityDefVO(
        Long id,
        String slug,
        @Schema(description = "由受信任代码资源目录解析的客户端 API 路径", example = "/system/users") String apiPath,
        JsonNode config,
        Boolean builtin,
        Boolean enabled,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
