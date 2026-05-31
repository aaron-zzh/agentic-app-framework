package com.xuejiai.aaf.module.developer.vo;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DeveloperRedeemCodeCreateDTO(
        @NotNull @Min(1) Long tokenAmount, LocalDateTime expiresAt, String remark) {}
