package com.xuejiai.aaf.module.ai.aigc.voice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 声音复刻更新请求（仅允许修改备注/别名）。 */
public record AiClonedVoiceUpdateDTO(
        @Schema(description = "音色别名（字母/数字/下划线，≤16字符）")
                @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "仅允许字母、数字和下划线")
                @Size(max = 16, message = "别名不超过16个字符")
                String preferredName,
        @Schema(description = "备注") String remark) {}
