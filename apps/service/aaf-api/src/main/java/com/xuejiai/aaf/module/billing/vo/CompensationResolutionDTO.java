package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompensationResolutionDTO(@NotBlank @Size(max = 500) String result) {}
