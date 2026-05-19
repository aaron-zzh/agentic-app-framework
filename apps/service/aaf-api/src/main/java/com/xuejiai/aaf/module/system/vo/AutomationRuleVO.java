package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDateTime;

/** 自动化规则响应。 */
public record AutomationRuleVO(
        Long id,
        String name,
        String entitySlug,
        String triggerType,
        String conditions,
        String actions,
        Boolean enabled,
        LocalDateTime createTime) {}
