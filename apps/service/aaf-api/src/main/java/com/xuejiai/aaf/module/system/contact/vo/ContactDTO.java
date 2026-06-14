package com.xuejiai.aaf.module.system.contact.vo;

import com.xuejiai.aaf.common.enums.sys.ContactSourceEnum;
import com.xuejiai.aaf.common.enums.sys.ContactStatusEnum;
import com.xuejiai.aaf.common.enums.sys.ContactTypeEnum;

import jakarta.validation.constraints.NotBlank;

/** 创建/更新联系人 DTO。 */
public record ContactDTO(
        @NotBlank String name,
        String realName,
        String phone,
        String email,
        String avatar,
        ContactTypeEnum type,
        ContactSourceEnum source,
        ContactStatusEnum status,
        Long parentId,
        String ext) {}
