package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiDigitalAvatarPageDTO extends PageParam {

    @Schema(description = "检测状态筛选：PENDING / PASSED / FAILED")
    private String detectStatus;
}
