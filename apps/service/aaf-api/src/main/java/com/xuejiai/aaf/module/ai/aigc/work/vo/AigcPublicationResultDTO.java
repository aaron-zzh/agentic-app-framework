package com.xuejiai.aaf.module.ai.aigc.work.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AigcPublicationResultDTO(
        @NotBlank String status,
        @Size(max = 200) String externalId,
        @Size(max = 1000) String externalUrl,
        String responseJson) {}
