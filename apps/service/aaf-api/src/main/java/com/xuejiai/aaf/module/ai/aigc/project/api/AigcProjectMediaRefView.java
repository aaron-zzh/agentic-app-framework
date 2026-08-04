package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcProjectMediaRefView(
        Long id,
        Long projectId,
        Long projectObjectId,
        Long mediaVersionId,
        String role,
        Integer orderNo,
        String adoptionStatus) {}
