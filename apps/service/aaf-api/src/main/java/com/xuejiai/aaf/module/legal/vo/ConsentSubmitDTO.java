package com.xuejiai.aaf.module.legal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 提交同意请求。
 *
 * <p>用户在弹窗中勾选"同意"后提交，每条文档对应一次记录。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "提交法律文档同意")
public record ConsentSubmitDTO(
        @Schema(description = "文档 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long documentId) {}
