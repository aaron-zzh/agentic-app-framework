package com.xuejiai.aaf.framework.intelligent.core.prompt;

/**
 * Prompt 型模型调用是否持有自主任务循环。
 *
 * <p>{@code AUTONOMOUS_AGENT_LOOP} 原名 {@code AUTONOMOUS_HARNESS}（AAF-108 #10803 原子重命名，不保留旧枚举别名）
 * ——实现不依赖官方 AgentScope Harness artifact，旧命名容易被误读为绑定官方组件；这是命名澄清，不改变运行模式合同。
 */
public enum InvocationMode {
    AUTONOMOUS_AGENT_LOOP,
    NON_AUTONOMOUS_L0
}
