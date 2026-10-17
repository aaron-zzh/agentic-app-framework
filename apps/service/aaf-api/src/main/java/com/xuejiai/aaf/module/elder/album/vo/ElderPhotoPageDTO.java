package com.xuejiai.aaf.module.elder.album.vo;

import com.xuejiai.aaf.common.enums.elder.PhotoSourceEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 长者照片分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "长者照片分页查询")
public class ElderPhotoPageDTO extends PageParam {

    private Long elderId;
    private Long deviceId;

    @InEnum(value = PhotoSourceEnum.class, message = "照片来源必须是 {value}")
    private String source;
}
