package com.xuejiai.aaf.framework.intelligent.core.context;

/** 上下文压缩策略。 */
public enum ContextPolicy {
    BALANCED,
    AGGRESSIVE,
    PRESERVE_RECENT,
    FULL_DETAIL;

    public static ContextPolicy from(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        return switch (value.trim().toLowerCase().replace('_', '-')) {
            case "aggressive" -> AGGRESSIVE;
            case "preserve-recent" -> PRESERVE_RECENT;
            case "full-detail" -> FULL_DETAIL;
            default -> BALANCED;
        };
    }
}
