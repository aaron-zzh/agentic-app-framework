package com.xuejiai.aaf.framework.intelligent.core.agent;

/**
 * Agent 执行接口——AAF 对 Agent 执行能力的统一抽象。
 * 上层只依赖此接口，底层实现可替换（当前：AgentScope ReActAgent）。
 */
public interface AgentExecutor {

    /**
     * 执行 Agent 任务。
     *
     * @param input 输入消息（文本）
     * @return 执行结果
     */
    AgentResult execute(String input);

    /**
     * 中断正在执行的任务。
     */
    void interrupt();

    /**
     * 获取 Agent 名称。
     */
    String getName();

    /**
     * 重置 Agent 内部状态（池化归还前调用）。
     */
    void reset();

    /** Agent 执行结果 */
    record AgentResult(boolean success, String output, String error) {
        public static AgentResult success(String output) {
            return new AgentResult(true, output, null);
        }
        public static AgentResult error(String error) {
            return new AgentResult(false, null, error);
        }
    }
}
