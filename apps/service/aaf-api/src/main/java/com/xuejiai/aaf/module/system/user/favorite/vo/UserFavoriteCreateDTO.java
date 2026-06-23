package com.xuejiai.aaf.module.system.user.favorite.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserFavoriteCreateDTO(
        @NotBlank String targetType, @NotNull Long targetId, String note) {}
