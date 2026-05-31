package com.xuejiai.aaf.module.developer.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** 开发者订阅套餐分页查询参数。 */
@Getter
@Setter
@Schema(description = "开发者订阅套餐分页查询参数")
public class DeveloperSubscriptionPlanPageParam extends PageParam {

    @Schema(description = "关键词，匹配编码/名称")
    private String keyword;

    @Schema(description = "状态：ENABLED/DISABLED")
    private String status;

    @Schema(description = "是否允许托管模型网关")
    private Boolean allowManagedGateway;

    @Schema(description = "是否允许开通子代理")
    private Boolean allowSubProxy;
}
