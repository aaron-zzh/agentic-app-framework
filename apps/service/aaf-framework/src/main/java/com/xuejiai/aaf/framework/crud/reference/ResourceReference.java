package com.xuejiai.aaf.framework.crud.reference;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 可信资源记录引用，仅包含资源标识和记录 ID。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "资源记录引用")
public record ResourceReference(
        @NotBlank @Schema(description = "资源标识") String resource,
        @Positive @Schema(description = "记录 ID") Long id) {}
