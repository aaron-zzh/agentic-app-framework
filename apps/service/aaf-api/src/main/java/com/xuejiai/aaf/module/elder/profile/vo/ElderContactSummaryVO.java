package com.xuejiai.aaf.module.elder.profile.vo;

import java.util.Set;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 长者详情下挂的联系人摘要；仅随 detail 字段集输出。 */
@Schema(description = "长者联系人摘要")
public record ElderContactSummaryVO(
        Long id,
        Long userId,
        String name,
        String mobile,
        @DictFormat(DictType.Elder.CONTACT_RELATIONSHIP) String relationship,
        Integer priority,
        Boolean emergencyContact,
        Set<String> authorizationScopes) {}
