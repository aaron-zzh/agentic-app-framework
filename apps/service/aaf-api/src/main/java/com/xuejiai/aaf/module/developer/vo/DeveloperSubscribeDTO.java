package com.xuejiai.aaf.module.developer.vo;

import jakarta.validation.constraints.NotBlank;

public record DeveloperSubscribeDTO(@NotBlank String planCode) {}
