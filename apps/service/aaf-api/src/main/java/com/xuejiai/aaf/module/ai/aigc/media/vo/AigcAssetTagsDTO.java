package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record AigcAssetTagsDTO(@NotNull List<@NotNull Long> tagIds) {}
