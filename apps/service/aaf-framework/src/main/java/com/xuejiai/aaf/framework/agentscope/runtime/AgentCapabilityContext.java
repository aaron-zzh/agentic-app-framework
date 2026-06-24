/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.runtime;

/**
 * Agent 能力标识上下文——区分不同 Agent 类型的积分结算分类。
 *
 * <p>在 AG-UI 请求线程（executor.submit 内）按 resolvedAgentId 写入，
 * 由 {@link com.xuejiai.aaf.framework.agentscope.middleware.CallLogMiddleware} 读取，
 * 用于 {@code credit_transaction.category} 字段，区分文案生成与普通对话。
 */
public final class AgentCapabilityContext {

    private static final ThreadLocal<String> CTX = new ThreadLocal<>();

    private AgentCapabilityContext() {}

    public static void set(String capability) {
        CTX.set(capability);
    }

    public static String get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }
}
