package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 字段级权限。
 *
 * @author AaronZZH & Kiro
 */
public record FieldAccessVO(
        @Schema(description = "是否可见") boolean visible,
        @Schema(description = "是否可编辑") boolean editable) {}
