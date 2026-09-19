package com.xuejiai.aaf.module.document.vo;

import java.util.List;

import com.xuejiai.aaf.module.document.api.DocumentVersionSemanticMetadataApi.SemanticTopic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 人工覆盖精确文档版本的语义元数据。 */
public record DocumentVersionSemanticMetadataUpdateDTO(
        @NotNull @PositiveOrZero Integer expectedMetadataVersion,
        @Size(max = 10000) String summary,
        @NotNull List<@Valid SemanticTopic> topics,
        @Size(max = 32) String language) {}
