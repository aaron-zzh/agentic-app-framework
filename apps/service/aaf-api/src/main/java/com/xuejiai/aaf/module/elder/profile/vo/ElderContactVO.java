package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 长者联系人响应；姓名、电话、账号及授权范围仅在 detail 字段集输出。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "长者联系人")
public record ElderContactVO(
        Long id,
        Integer version,
        Long elderId,
        Long userId,
        String name,
        String mobile,
        @DictFormat(DictType.Elder.CONTACT_RELATIONSHIP) String relationship,
        Integer priority,
        Boolean emergencyContact,
        Set<String> authorizationScopes,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO elder,
        ResourceRefDTO user,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
