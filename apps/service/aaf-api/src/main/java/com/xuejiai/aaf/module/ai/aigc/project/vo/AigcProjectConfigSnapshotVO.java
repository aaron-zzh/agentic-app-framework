package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcExecutionBindingVersionRef;

public record AigcProjectConfigSnapshotVO(
        Long id,
        Integer revisionNo,
        String projectTypeVersion,
        String blueprintVersion,
        String domainExtensionVersion,
        List<Long> channelVersions,
        List<AigcExecutionBindingVersionRef> executionBindingVersions,
        Map<String, Object> compatibilityResult,
        Map<String, Object> snapshot) {}
