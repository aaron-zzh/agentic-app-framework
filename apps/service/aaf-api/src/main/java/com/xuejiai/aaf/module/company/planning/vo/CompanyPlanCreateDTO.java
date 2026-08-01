package com.xuejiai.aaf.module.company.planning.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 企业规划创建入参。 */
public record CompanyPlanCreateDTO(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 32) String planType,
        @NotBlank @Size(max = 16) String period,
        @NotNull Integer year,
        @Min(1) @Max(4) Integer quarter,
        String content) {}
