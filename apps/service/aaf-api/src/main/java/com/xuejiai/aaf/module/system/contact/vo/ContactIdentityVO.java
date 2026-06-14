package com.xuejiai.aaf.module.system.contact.vo;

import java.time.LocalDateTime;

/** 联系人渠道身份响应 VO。 */
public record ContactIdentityVO(
        Long id,
        Long contactId,
        String channel,
        String externalId,
        String corpId,
        String displayName,
        String avatarUrl,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
