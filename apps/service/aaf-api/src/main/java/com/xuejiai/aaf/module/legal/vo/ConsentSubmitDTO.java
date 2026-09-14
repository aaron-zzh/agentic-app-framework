package com.xuejiai.aaf.module.legal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 提交同意请求。
 *
 * <p>用户在弹窗中勾选"同意"后提交，每条文档对应一次记录。{@code documentVersionId} 必须等于用户实际看到的待同意精确版本；
 * 服务端据此拒绝已过期的隐式同意，避免用户同意了未阅读的更新版本。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "提交法律文档同意")
public record ConsentSubmitDTO(
        @Schema(description = "文档 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long documentId,
        @Schema(description = "用户实际阅读的精确不可变文档版本 ID", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @Positive
                Long documentVersionId) {}
