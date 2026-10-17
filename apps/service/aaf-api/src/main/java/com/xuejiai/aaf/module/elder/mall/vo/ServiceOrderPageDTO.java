package com.xuejiai.aaf.module.elder.mall.vo;

import com.xuejiai.aaf.common.enums.elder.ServiceOrderStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 服务预约分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "服务预约分页查询")
public class ServiceOrderPageDTO extends PageParam {

    private Long elderId;
    private Long serviceItemId;

    @InEnum(value = ServiceOrderStatusEnum.class, message = "服务预约状态必须是 {value}")
    private String status;
}
