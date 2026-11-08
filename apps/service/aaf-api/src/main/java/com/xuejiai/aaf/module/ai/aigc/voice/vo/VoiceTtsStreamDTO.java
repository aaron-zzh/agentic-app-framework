package com.xuejiai.aaf.module.ai.aigc.voice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 流式语音合成请求。 */
public record VoiceTtsStreamDTO(
        @Schema(description = "待合成文本（≤2000字符）")
                @NotBlank(message = "合成文本不能为空")
                @Size(max = 2000, message = "合成文本不超过2000个字符")
                String text,
        @Schema(description = "音色编码，为空时使用默认音色") @Size(max = 128, message = "音色编码不超过128个字符")
                String voice) {}
