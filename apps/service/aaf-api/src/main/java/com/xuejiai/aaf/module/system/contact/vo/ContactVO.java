package com.xuejiai.aaf.module.system.contact.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.sys.ContactSourceEnum;
import com.xuejiai.aaf.common.enums.sys.ContactStatusEnum;
import com.xuejiai.aaf.common.enums.sys.ContactTypeEnum;

/** 联系人响应 VO。 */
public record ContactVO(
        Long id,
        String name,
        String realName,
        String phone,
        String email,
        String avatar,
        ContactTypeEnum type,
        ContactSourceEnum source,
        ContactStatusEnum status,
        Long parentId,
        String ext,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
