package com.xuejiai.aaf.module.livechat.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 提交评价请求 DTO。 */
public record RatingSubmitDTO(
        @NotNull Long conversationId,
        Long userId,
        Long staffId,
        @NotNull @Min(1) @Max(5) Integer score,
        String comment) {}
