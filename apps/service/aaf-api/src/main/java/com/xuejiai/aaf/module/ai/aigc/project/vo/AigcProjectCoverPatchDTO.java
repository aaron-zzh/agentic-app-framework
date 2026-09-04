package com.xuejiai.aaf.module.ai.aigc.project.vo;

import jakarta.validation.constraints.NotNull;

/** 项目封面明确更新请求。 */
public record AigcProjectCoverPatchDTO(
        @NotNull AigcProjectCoverOperation operation,
        Long fileId,
        String prompt,
        String idempotencyKey) {}
