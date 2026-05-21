package com.xuejiai.aaf.module.system.dict.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "更新字典类型")
public record DictTypeUpdateDTO(
        @Size(max = 100) String name, Integer status, String remark) {}
