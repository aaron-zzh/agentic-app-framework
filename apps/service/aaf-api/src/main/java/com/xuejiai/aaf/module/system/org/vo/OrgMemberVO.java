package com.xuejiai.aaf.module.system.org.vo;

import java.time.LocalDateTime;

/** 组织成员响应。 */
public record OrgMemberVO(
        Long id, Long orgId, Long userId, String role, LocalDateTime createTime) {}
