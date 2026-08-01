package com.xuejiai.aaf.module.company.okr.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** OKR 目标创建入参。 */
public record ObjectiveCreateDTO(
        @NotBlank @Size(max = 256) String title,
        Long planId,
        Long parentId,
        Long ownerUserId,
        @Size(max = 16) String period) {}
