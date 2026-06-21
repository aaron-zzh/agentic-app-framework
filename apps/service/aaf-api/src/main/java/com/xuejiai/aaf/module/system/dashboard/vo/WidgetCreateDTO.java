package com.xuejiai.aaf.module.system.dashboard.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 组件创建/保存请求。
 *
 * <p>前端发送结构化的 {@code position} 与 {@code config}，服务端在落盘时序列化为 JSON 文本写入 jsonb 列。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "创建/保存仪表盘组件")
public record WidgetCreateDTO(
        /** 客户端可选填，如预设里的语义化 id；DB 持久化时由数据库生成自增主键 */
        String id,
        @NotBlank @Size(max = 20) String type,
        @NotBlank @Size(max = 100) String title,
        @NotNull WidgetPositionVO position,
        @NotNull Map<String, Object> config,
        Integer sortOrder) {}
