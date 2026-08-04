package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;
import java.util.Map;

public record AigcProjectConfigSnapshotVO(
        Long id,
        Integer revisionNo,
        String projectTypeVersion,
        String blueprintVersion,
        String domainExtensionVersion,
        List<Long> channelVersions,
        List<Long> executionBindingVersions,
        Map<String, Object> compatibilityResult,
        Map<String, Object> snapshot) {}
