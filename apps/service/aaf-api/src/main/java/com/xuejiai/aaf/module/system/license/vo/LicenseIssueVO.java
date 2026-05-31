package com.xuejiai.aaf.module.system.license.vo;

import java.time.Instant;
import java.util.Set;

/** 官方 license.jwt 签发结果。 */
public record LicenseIssueVO(
        String token, String subject, String tier, boolean owner, Set<String> features, Instant expiresAt) {}
