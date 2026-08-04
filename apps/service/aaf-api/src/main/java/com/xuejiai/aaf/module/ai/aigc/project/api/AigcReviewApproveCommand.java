package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcReviewApproveCommand(
        Long projectId, Long reviewObjectId, Integer expectedProjectVersion, String conclusion) {}
