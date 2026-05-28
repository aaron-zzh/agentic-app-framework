package com.xuejiai.aaf.module.system.dict.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 字典数据响应 VO。
 *
 * @author AaronZZH & Kiro
 */
public record DictDataVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "字典类型编码") String dictType,
        @Schema(description = "字典标签") String label,
        @Schema(description = "字典键值") String value,
        @Schema(description = "排序") Integer sort,
        @Schema(description = "状态（0 正常 / 1 禁用）") Integer status,
        @Schema(description = "标签颜色") String colorType,
        @Schema(description = "CSS 样式") String cssClass,
        @Schema(description = "备注") String remark,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
