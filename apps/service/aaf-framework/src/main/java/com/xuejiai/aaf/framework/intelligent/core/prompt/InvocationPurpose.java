package com.xuejiai.aaf.framework.intelligent.core.prompt;

/** Prompt 型模型调用的稳定语义目的。 */
public enum InvocationPurpose {
    HARNESS_EXECUTION,
    ROLE_SELECTION,
    SKILL_SELECTION,
    CONTEXT_SUMMARY,
    PARAMETER_EXTRACTION,
    MEMORY_EXTRACTION,
    MEMORY_DEDUPLICATION,
    CLASSIFICATION,
    JUDGE
}
