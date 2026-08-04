package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 创建项目类型兼容包草稿。 */
public record AigcProjectTypePackageCreateDTO(
        @NotBlank String packageVersion,
        @NotNull Long projectTypeId,
        @NotNull Long blueprintId,
        Long domainExtensionId,
        List<Long> channelSpecIds,
        List<Long> executionBindingIds,
        @NotBlank String productionMode) {

    public AigcProjectTypePackageCreateDTO {
        channelSpecIds = channelSpecIds == null ? List.of() : List.copyOf(channelSpecIds);
        executionBindingIds =
                executionBindingIds == null ? List.of() : List.copyOf(executionBindingIds);
    }
}
