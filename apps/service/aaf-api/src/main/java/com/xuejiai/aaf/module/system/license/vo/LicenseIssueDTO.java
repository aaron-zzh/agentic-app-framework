package com.xuejiai.aaf.module.system.license.vo;

import java.time.Instant;
import java.util.Set;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 官方 license.jwt 签发参数。 */
public record LicenseIssueDTO(
        String subject,
        @NotBlank String tier,
        String org,
        Set<String> features,
        @NotNull @Future Instant expiresAt) {}
