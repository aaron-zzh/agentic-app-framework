package com.xuejiai.aaf.module.system.entity.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作区实体元数据启动响应。
 *
 * <p>资源目录仅来自启动期编译成功的 {@code CrudResourceRegistry}，包含没有独立 UI 实体定义的关系资源。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "工作区实体元数据启动响应")
public record EntityDefBootstrapVO(
        @Schema(description = "已启用的实体定义") List<EntityDefVO> definitions,
        @Schema(description = "受信任代码资源目录") List<CodeEntityResourceVO> resources) {}
