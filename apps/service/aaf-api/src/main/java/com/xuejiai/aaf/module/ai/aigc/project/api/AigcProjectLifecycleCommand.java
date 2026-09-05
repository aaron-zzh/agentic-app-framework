package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectLifecycleCommand(
        Long projectId, Integer expectedProjectVersion, String idempotencyKey, String reason) {}
