package com.xuejiai.aaf.framework.intelligent.assistant;

/** 当前请求中的 AI 助理委托上下文。 */
public final class AssistantContextHolder {

    private static final ThreadLocal<AssistantContext> HOLDER = new ThreadLocal<>();

    private AssistantContextHolder() {}

    public static void set(AssistantContext context) {
        HOLDER.set(context);
    }

    public static AssistantContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record AssistantContext(Long assistantDefinitionId, String assistantId, Long delegatorId) {}
}
