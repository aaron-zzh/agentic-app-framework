package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 数字人形象分页查询参数。 */
@Schema(description = "数字人形象分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiDigitalAvatarPageDTO extends PageParam {

    @Schema(description = "检测状态过滤：PENDING / PASSED / FAILED")
    private String detectStatus;
}
