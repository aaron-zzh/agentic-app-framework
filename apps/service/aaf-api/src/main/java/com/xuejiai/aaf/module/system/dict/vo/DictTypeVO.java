package com.xuejiai.aaf.module.system.dict.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 字典类型响应 VO。
 *
 * @author AaronZZH & Kiro
 */
public record DictTypeVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "字典名称") String name,
        @Schema(description = "字典类型编码") String type,
        @Schema(description = "状态（0 正常 / 1 禁用）") Integer status,
        @Schema(description = "备注") String remark,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
