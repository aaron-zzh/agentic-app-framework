package com.xuejiai.aaf.framework.intelligent.assistant.model;

/** canonical structured clarification 已原子提交后的工具挂起信号。 */
public final class ClarificationRequiredException extends IllegalStateException {
    private final String requestId;

    public ClarificationRequiredException(String requestId, String message) {
        super(message);
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId 不能为空白");
        }
        this.requestId = requestId;
    }

    public String requestId() {
        return requestId;
    }
}
