package com.xuejiai.aaf.module.developer.vo;

import java.time.Instant;
import java.time.LocalDateTime;

public record DeveloperApiKeyVO(
        Long id,
        String name,
        String keyPrefix,
        String scopes,
        Boolean enabled,
        Instant expiresAt,
        Instant lastUsedAt,
        LocalDateTime createTime) {}
