package com.xuejiai.aaf.module.system.dict.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建字典类型")
/**
 * @author AaronZZH & Kiro
 */
public record DictTypeCreateDTO(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String type,
        String remark) {}
