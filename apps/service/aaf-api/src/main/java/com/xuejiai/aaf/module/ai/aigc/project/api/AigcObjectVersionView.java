package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcObjectVersionView(
        Long id, Long objectId, Integer versionNo, String status, Long executionRunId) {}
