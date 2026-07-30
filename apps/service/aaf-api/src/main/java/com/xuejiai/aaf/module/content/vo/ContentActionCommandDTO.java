package com.xuejiai.aaf.module.content.vo;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
 * 内容动作命令。
 *
 * @author AaronZZH & Kiro
 */
public record ContentActionCommandDTO(
        @NotBlank String actionKey,
        Long objectId,
        String prompt,
        List<Long> snippetIds,
        List<String> attachmentRefs,
        String generationMode,
        Boolean confirmed) {}
