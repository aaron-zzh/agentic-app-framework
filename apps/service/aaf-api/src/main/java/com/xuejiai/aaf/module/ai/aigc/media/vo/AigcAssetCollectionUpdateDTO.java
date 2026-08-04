package com.xuejiai.aaf.module.ai.aigc.media.vo;

import jakarta.validation.constraints.NotBlank;

public record AigcAssetCollectionUpdateDTO(
        @NotBlank String name, String collectionType, String description) {}
