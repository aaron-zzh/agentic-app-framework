package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentBrandProfileKindEnum;
import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌/IP 资料分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "品牌/IP 资料分页查询")
public class ContentBrandProfilePageDTO extends PageParam {

    @InEnum(value = ContentBrandProfileKindEnum.class, message = "kind 必须是 {value}")
    @Schema(description = "kind 筛选")
    private String kind;

    @InEnum(value = ContentConfigStatusEnum.class, message = "status 必须是 {value}")
    @Schema(description = "status 筛选")
    private String status;
}
