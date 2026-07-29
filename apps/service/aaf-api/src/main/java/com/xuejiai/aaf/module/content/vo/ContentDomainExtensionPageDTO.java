package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 行业扩展分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "行业扩展分页查询")
public class ContentDomainExtensionPageDTO extends PageParam {

    @InEnum(value = ContentConfigStatusEnum.class, message = "status 必须是 {value}")
    @Schema(description = "status 筛选")
    private String status;
}
