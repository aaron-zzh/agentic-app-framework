package com.xuejiai.aaf.module.system.role.vo;

import java.time.LocalDateTime;

/** 数据权限规则响应。 */
public record DataAccessRuleVO(
        Long id,
        String entitySlug,
        String roles,
        String condition,
        String effect,
        LocalDateTime createTime) {}
