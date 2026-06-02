package com.xuejiai.aaf.framework.intelligent.agentscope.memory;

import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;

/**
 * AAF 上下文管理适配器——封装 AgentScope AutoContextMemory。
 *
 * <h3>使用流程</h3>
 *
 * <pre>
 * 1. AAF MemoryPipeline 检索 → 产出 MemoryContext（P0-P5 内容）
 * 2. Hook（PreReasoningEvent）将 MemoryContext 注入到 Agent 对话历史
 * 3. AutoContextMemory 检测总 Token 数 → 超限则渐进式压缩
 * 4. 最终上下文送 LLM（保证不超 Token 窗口）
 *
 * AAF MemoryPipeline 负责"选什么放进来"（P0-P5 优先级检索）
 * AutoContextMemory 负责"放不下时怎么压缩"（渐进式 6 策略）
 * 两者串联配合，不冲突。
 * </pre>
 *
 * <h3>P0-P5 与压缩策略的映射</h3>
 *
 * <ul>
 *   <li>P0（System Prompt + 当前消息）→ 永不压缩（AutoContextMemory 保护当前轮次）
 *   <li>P1-P2（工作记忆 + 短期记忆）→ lastKeep 保护最近 N 条
 *   <li>P3（知识库检索结果）→ 作为工具调用结果，超限时优先压缩
 *   <li>P4-P5（画像 + 情景记忆）→ 注入为早期消息，最先被压缩/卸载
 * </ul>
 *
 * <h3>集成方式</h3>
 *
 * <pre>
 * // 在 AgentFactory 创建 Agent 时配置 Memory：
 * var memory = AafAutoContextMemoryAdapter.create(chatModel);
 * var agent = ReActAgent.builder()
 *     .memory(memory)
 *     .build();
 * </pre>
 */
public class AafAutoContextMemoryAdapter {

    private AafAutoContextMemoryAdapter() {}

    /**
     * 创建配置好的 AutoContextMemory 实例。
     *
     * @param model 用于压缩摘要的模型（可用轻量模型降低成本）
     * @return 配置好的 Memory 实例
     */
    public static Memory create(Model model) {
        var config =
                AutoContextConfig.builder()
                        // 最大 Token 窗口
                        .maxToken(100_000)
                        // 消息数阈值：超过时触发压缩
                        .msgThreshold(50)
                        // 保护最近 N 条消息不被压缩（对应 P0-P2）
                        .lastKeep(10)
                        // 大内容卸载阈值（单条消息超过此字符数则卸载）
                        .largePayloadThreshold(2000)
                        // 最少连续工具调用消息数才触发工具历史压缩
                        .minConsecutiveToolMessages(6)
                        .build();

        return new AutoContextMemory(config, model);
    }

    /**
     * 创建自定义配置的 AutoContextMemory。
     *
     * @param model 压缩摘要模型
     * @param config 自定义配置
     * @return Memory 实例
     */
    public static Memory create(Model model, AutoContextConfig config) {
        return new AutoContextMemory(config, model);
    }
}
