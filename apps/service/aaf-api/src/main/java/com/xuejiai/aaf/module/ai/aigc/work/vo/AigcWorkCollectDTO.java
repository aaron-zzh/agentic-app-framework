package com.xuejiai.aaf.module.ai.aigc.work.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AigcWorkCollectDTO(
        @NotNull Long projectId,
        @NotNull Long deliverableSetObjectId,
        @NotNull Long manifestObjectVersionId,
        @NotNull Integer expectedProjectVersion,
        Long coverMediaVersionId,
        @Size(max = 32) String visibility,
        @NotBlank String idempotencyKey) {}
