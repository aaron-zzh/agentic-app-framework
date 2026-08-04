package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectMediaRefCommand(
        Long projectId,
        Long projectObjectId,
        Long mediaVersionId,
        String role,
        Integer orderNo,
        String adoptionStatus,
        Integer expectedProjectVersion) {}
