package com.xuejiai.aaf.module.ai.aigc.project.api;

public record AigcApprovedManifestView(
        Long projectId,
        Long deliverableSetObjectId,
        Long manifestObjectVersionId,
        Long reviewObjectId,
        String evidenceHash) {}
