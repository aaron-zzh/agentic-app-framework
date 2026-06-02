package com.xuejiai.aaf.framework.intelligent.agentscope.memory;

import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;

/**
 * AAF 上下文管理适配器——封装 AgentScope AutoContextMemory。
 *
 * <h3>职责定位</h3>
 *
 * <p>本类只负责一件事：<b>对话消息列表的存储与自动压缩</b>。
 * ReActAgent 内部自动调用 {@code addMessage()} 追加每轮消息，
 * 调用 {@code getMessages()} 获取 LLM 输入——外部无需手动操作。
 *
 * <p>与 {@code MemoryContextHook}（记忆/知识库检索注入）<b>完全独立</b>：
 * <ul>
 *   <li>本类：管消息列表的存储和超限压缩（替代裸 InMemoryMemory）
 *   <li>MemoryContextHook：每轮 LLM 调用前检索长期记忆+知识库，临时注入到 inputMessages
 *   <li>两者作用时机不同、互不依赖、可任意组合
 * </ul>
 *
 * <h3>使用流程</h3>
 *
 * <pre>
 * 用户消息 → ReActAgent 自动 addMessage(userMsg)
 *   → AutoContextMemory 检测 Token 是否超限 → 超限则渐进式压缩
 *   → PreReasoningEvent 触发 → MemoryContextHook 注入检索结果（临时，不写回 Memory）
 *   → LLM 收到 [检索结果] + [压缩后的对话历史] + [当前消息]
 *   → LLM 推理 → ReActAgent 自动 addMessage(assistantMsg)
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
 * // 在 AgentScopeRuntime 创建 Agent 时配置 Memory：
 * var memory = AafAutoContextMemoryAdapter.create(chatModel);
 * var agent = ReActAgent.builder()
 *     .memory(memory)    // 传入后 ReActAgent 自动管理读写
 *     .hook(new AutoContextHook())  // 配套 Hook，触发压缩检查
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
