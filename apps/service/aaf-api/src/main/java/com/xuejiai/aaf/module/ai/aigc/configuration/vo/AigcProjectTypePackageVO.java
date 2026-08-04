package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;
import java.util.Map;

/** 项目类型兼容包信息。 */
public record AigcProjectTypePackageVO(
        Long id,
        Integer version,
        String packageVersion,
        Long projectTypeId,
        Long blueprintId,
        Long domainExtensionId,
        List<Long> channelSpecIds,
        List<Long> executionBindingIds,
        String productionMode,
        Map<String, Object> compatibilityResult,
        String status) {}
