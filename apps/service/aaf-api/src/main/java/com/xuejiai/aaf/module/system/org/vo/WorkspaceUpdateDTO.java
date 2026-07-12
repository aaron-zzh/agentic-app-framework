package com.xuejiai.aaf.module.system.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新工作区请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新工作区")
public record WorkspaceUpdateDTO(@Schema(description = "工作区名称") String name) {}
