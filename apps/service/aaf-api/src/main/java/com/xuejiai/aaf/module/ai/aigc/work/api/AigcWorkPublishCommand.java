package com.xuejiai.aaf.module.ai.aigc.work.api;

import java.time.Instant;

public record AigcWorkPublishCommand(
        Long workId, Long channelSpecVersionId, Instant scheduledAt, String idempotencyKey) {}
