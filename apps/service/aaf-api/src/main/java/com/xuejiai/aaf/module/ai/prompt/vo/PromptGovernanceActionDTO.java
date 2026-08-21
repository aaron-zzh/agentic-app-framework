package com.xuejiai.aaf.module.ai.prompt.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Prompt 治理单记录 Action 请求。 */
public record PromptGovernanceActionDTO(@NotNull @Positive Long id) {}
