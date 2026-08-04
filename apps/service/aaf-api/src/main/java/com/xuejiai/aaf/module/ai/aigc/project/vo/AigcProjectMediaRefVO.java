package com.xuejiai.aaf.module.ai.aigc.project.vo;

public record AigcProjectMediaRefVO(
        Long id,
        Long objectId,
        Long objectVersionId,
        Long mediaVersionId,
        String role,
        Integer sortOrder,
        String adoptionStatus) {}
