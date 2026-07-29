package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道规格分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "渠道规格分页查询")
public class ContentChannelSpecPageDTO extends PageParam {

    @InEnum(value = ContentConfigStatusEnum.class, message = "status 必须是 {value}")
    @Schema(description = "status 筛选")
    private String status;
}
