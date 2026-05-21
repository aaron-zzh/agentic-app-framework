package com.xuejiai.aaf.module.system.workflow.vo;

import java.time.LocalDateTime;

/** 归档规则响应。 */
public record ArchiveRuleVO(
        Long id,
        String entitySlug,
        String condition,
        Integer afterDays,
        Boolean enabled,
        LocalDateTime createTime) {}
