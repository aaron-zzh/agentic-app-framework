package com.xuejiai.aaf.module.system.entity.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 自定义字段信息。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "自定义字段")
public record CustomFieldVO(
        String name,
        String label,
        String type,
        List<String> options,
        @Schema(description = "是否隐藏") Boolean hidden) {}
