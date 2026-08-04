package com.xuejiai.aaf.module.ai.aigc.media.vo;

import jakarta.validation.constraints.NotBlank;

public record AigcAssetTagUpdateDTO(@NotBlank String name, String color) {}
