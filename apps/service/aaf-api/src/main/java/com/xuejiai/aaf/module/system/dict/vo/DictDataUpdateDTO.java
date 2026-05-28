package com.xuejiai.aaf.module.system.dict.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "更新字典数据")
/**
 * @author AaronZZH & Kiro
 */
public record DictDataUpdateDTO(
        @Size(max = 100) String label,
        @Size(max = 100) String value,
        Integer sort,
        Integer status,
        @Size(max = 50) String colorType,
        @Size(max = 100) String cssClass,
        String remark) {}
