package com.xuejiai.aaf.framework.intelligent.core.context;

/** 上下文压缩动作。 */
public enum ContextCompressionAction {
    NONE,
    RULE_TRUNCATE_LARGE_MESSAGE,
    DROP_OLD_HISTORY,
    SUMMARIZE_HISTORY
}
