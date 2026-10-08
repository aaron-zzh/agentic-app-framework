package com.xuejiai.aaf.module.digitalhuman.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** 数字人分页查询。 */
@Getter
@Setter
@Schema(description = "数字人分页查询")
public class DigitalHumanPageDTO extends PageParam {

    @Schema(description = "名称（模糊查询）")
    private String name;

    @Schema(description = "状态：active / archived")
    private String status;
}
