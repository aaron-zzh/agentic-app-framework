package com.xuejiai.aaf.module.ai.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重命名会话请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "重命名会话请求")
public record ChatSessionRenameDTO(
        @Schema(description = "新标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "我的对话")
                @NotBlank
                @Size(max = 200)
                String title) {}
