package com.xuejiai.aaf.module.system.license.vo;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 当前框架授权状态。 */
public record LicenseStatusVO(
        boolean identityValid,
        String tier,
        String userId,
        Instant expiresAt,
        String upgradeUrl,
        Set<String> features,
        List<String> licenseFileLocations) {}
