package com.xuejiai.aaf.module.ai.aigc.work.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AigcWorkCollectDTO(
        @NotNull Long projectId,
        @NotNull Long deliverableObjectId,
        @NotNull Long adoptedObjectVersionId,
        Long coverMediaVersionId,
        @Size(max = 32) String visibility) {}
