package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentObjectVersionStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容对象版本分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentObjectVersionPageDTO extends PageParam {

    private Long projectId;

    private Long objectId;

    @InEnum(value = ContentObjectVersionStatusEnum.class, message = "status 必须是 {value}")
    private String status;
}
