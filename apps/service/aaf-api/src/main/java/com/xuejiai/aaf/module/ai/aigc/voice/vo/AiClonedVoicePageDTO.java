package com.xuejiai.aaf.module.ai.aigc.voice.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 声音复刻分页查询参数。 */
@Schema(description = "声音复刻分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiClonedVoicePageDTO extends PageParam {

    @Schema(description = "按绑定模型过滤")
    private String targetModel;

    @Schema(description = "按用户 ID 过滤")
    private Long userId;
}
