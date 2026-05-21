package com.xuejiai.aaf.module.system.org.vo;

import java.time.LocalDateTime;

/** 组织响应。 */
public record OrganizationVO(
        Long id, String name, String slug, String type, Long ownerId, LocalDateTime createTime) {}
