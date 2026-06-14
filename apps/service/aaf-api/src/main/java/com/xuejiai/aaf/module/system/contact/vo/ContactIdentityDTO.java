package com.xuejiai.aaf.module.system.contact.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 创建/更新渠道身份 DTO。 */
public record ContactIdentityDTO(
        @NotNull Long contactId,
        @NotBlank String channel,
        @NotBlank String externalId,
        String corpId,
        String displayName,
        String avatarUrl) {}
