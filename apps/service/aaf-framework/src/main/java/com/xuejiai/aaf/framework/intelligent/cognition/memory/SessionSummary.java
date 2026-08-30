/**
 * 短期会话摘要。
 *
 * @author Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 被挤出最近窗口的旧轮次的结构化摘要。
 *
 * <p>只承载"已确认决定/已验证事实/未决问题"三类语义化字段，不做原文等价改写；调用方必须把 {@code content}
 * 标记为低信任 USER 上下文，不得当作 SYSTEM 指令或已核实事实的替代来源。
 *
 * @param content 严格 JSON 摘要正文（低信任，需与 sourceMessageIds 一同披露）
 * @param sourceMessageIds 本次摘要覆盖的原始消息 ID，用于可追溯与后续增量摘要的游标推进
 * @param coveredThroughTimestamp 本次摘要覆盖到的最后一条原始消息时间，晚于此时间的消息不在摘要范围内
 * @param generatedAt 摘要生成时间
 */
public record SessionSummary(
        String content,
        List<String> sourceMessageIds,
        Instant coveredThroughTimestamp,
        Instant generatedAt) {

    public SessionSummary {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 不能为空白");
        }
        sourceMessageIds =
                List.copyOf(Objects.requireNonNull(sourceMessageIds, "sourceMessageIds 不能为空"));
        if (sourceMessageIds.isEmpty()) {
            throw new IllegalArgumentException("sourceMessageIds 不能为空集合");
        }
        Objects.requireNonNull(coveredThroughTimestamp, "coveredThroughTimestamp 不能为空");
        Objects.requireNonNull(generatedAt, "generatedAt 不能为空");
    }
}
