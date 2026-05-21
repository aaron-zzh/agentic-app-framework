package com.xuejiai.aaf.module.system.dict.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建字典数据")
public record DictDataCreateDTO(
        @NotBlank @Size(max = 100) String dictType,
        @NotBlank @Size(max = 100) String label,
        @NotBlank @Size(max = 100) String value,
        Integer sort,
        @Size(max = 50) String colorType,
        @Size(max = 100) String cssClass,
        String remark) {}
