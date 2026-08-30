/**
 * 会话语义压缩边界。
 *
 * @author Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryMessage;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.SessionSummary;

/**
 * 短期会话上下文的独立摘要边界，不复用执行级 {@link ContextCompressionPort}。
 *
 * <p>两者作用对象与生命周期不同：{@code ContextCompressionPort}
 * 是单次 Harness 调用前的全局 Prompt 预算治理，随执行画像冻结；本端口只处理被挤出短期记忆最近窗口的旧会话轮次，
 * 跨多次物理调用增量维护，失败时降级为保留旧摘要，不阻断当前执行。
 */
public interface SessionContextCompressionPort {

    /**
     * 对被挤出窗口的旧轮次生成结构化摘要。
     *
     * <p>调用方必须保证 {@code messagesToSummarize} 非空且已按时间正序排列；失败或超时时抛出异常，
     * 调用方按"保留旧摘要 + 最新原文尾部"降级，不得阻断主执行。
     *
     * @param messagesToSummarize 待摘要的旧轮次原文（已被排除在最近保留窗口之外）
     * @param previousSummary 上一次冻结的摘要，为空表示本会话首次摘要
     * @param meteringUserId 用于 Token 计量的用户 ID，可为空
     */
    SessionSummary summarize(
            List<MemoryMessage> messagesToSummarize,
            Optional<SessionSummary> previousSummary,
            Long meteringUserId);
}
