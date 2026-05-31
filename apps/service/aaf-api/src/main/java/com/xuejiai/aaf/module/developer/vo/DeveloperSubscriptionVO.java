package com.xuejiai.aaf.module.developer.vo;

import java.time.LocalDateTime;

public record DeveloperSubscriptionVO(
        Long id,
        String planCode,
        String planName,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status) {}
