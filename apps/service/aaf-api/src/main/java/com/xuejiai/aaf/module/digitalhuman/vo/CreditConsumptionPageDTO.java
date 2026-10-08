package com.xuejiai.aaf.module.digitalhuman.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** 数字人积分消耗记录分页查询。 */
@Getter
@Setter
@Schema(description = "数字人积分消耗记录分页查询")
public class CreditConsumptionPageDTO extends PageParam {

    @Schema(description = "数字人 ID")
    private Long digitalHumanId;

    @Schema(description = "项目 ID")
    private Long projectId;

    @Schema(description = "操作类型")
    private String operation;
}
