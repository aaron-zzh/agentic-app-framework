package com.xuejiai.aaf.module.elder.profile.vo;

import com.xuejiai.aaf.common.enums.elder.ContactRelationshipEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 长者联系人分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "长者联系人分页查询")
public class ElderContactPageDTO extends PageParam {

    private Long elderId;
    private Long userId;

    @InEnum(value = ContactRelationshipEnum.class, message = "联系人关系必须是 {value}")
    private String relationship;

    private Boolean emergencyContact;
}
