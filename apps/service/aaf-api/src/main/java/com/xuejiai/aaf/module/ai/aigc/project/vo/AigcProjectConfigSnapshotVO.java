package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectBlueprintSnapshotPayload;

public record AigcProjectConfigSnapshotVO(
        Long id,
        Integer revisionNo,
        String sourceType,
        String sourceRef,
        String snapshotHash,
        String projectTypeVersion,
        String blueprintVersion,
        List<Long> channelVersions,
        List<AigcExecutionBindingVersionRef> executionBindingVersions,
        Map<String, Object> compatibilityResult,
        AigcProjectBlueprintSnapshotPayload snapshot) {}
