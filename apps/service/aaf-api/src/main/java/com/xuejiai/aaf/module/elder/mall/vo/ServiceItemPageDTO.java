package com.xuejiai.aaf.module.elder.mall.vo;

import com.xuejiai.aaf.common.enums.elder.ServiceItemStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 服务项目分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "服务项目分页查询")
public class ServiceItemPageDTO extends PageParam {

    private String name;
    private String category;

    @InEnum(value = ServiceItemStatusEnum.class, message = "服务项目状态必须是 {value}")
    private String status;
}
