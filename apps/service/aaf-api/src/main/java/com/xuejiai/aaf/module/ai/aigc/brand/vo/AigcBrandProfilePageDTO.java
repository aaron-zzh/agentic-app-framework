package com.xuejiai.aaf.module.ai.aigc.brand.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 品牌/IP 资料分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AIGC 品牌/IP 资料分页查询")
public class AigcBrandProfilePageDTO extends PageParam {

    private String kind;
    private String status;
}
