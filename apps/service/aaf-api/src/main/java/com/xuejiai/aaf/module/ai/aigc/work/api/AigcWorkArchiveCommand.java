package com.xuejiai.aaf.module.ai.aigc.work.api;

public record AigcWorkArchiveCommand(
        Long workId,
        Integer expectedProjectVersion,
        Integer expectedWorkVersion, String idempotencyKey, String reason) {}
