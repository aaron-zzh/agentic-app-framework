package com.xuejiai.aaf.module.ai.prompt.vo;

import java.time.LocalDateTime;
import java.util.List;

/** Prompt 不可变版本治理视图。 */
public record PromptVersionVO(
        Long id,
        Integer version,
        String status,
        String prompt,
        String negativePrompt,
        String model,
        Integer width,
        Integer height,
        Integer steps,
        Long seed,
        List<String> variables,
        String contentHash,
        String changeSummary,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
