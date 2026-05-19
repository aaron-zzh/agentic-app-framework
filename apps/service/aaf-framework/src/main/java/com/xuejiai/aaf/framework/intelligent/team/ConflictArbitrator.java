/**
 * 冲突仲裁服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.team;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.Setter;

/**
 * 结果冲突检测、投票机制、人工升级。
 * 当多个 Agent 对同一问题给出不同答案时进行仲裁。
 */
@Service
public class ConflictArbitrator {

    /** 冲突检测阈值：置信度差距小于此值视为冲突 */
    private static final double CONFLICT_THRESHOLD = 0.15;

    /**
     * 检测结果冲突。
     *
     * @param results 多个 Agent 的结果
     * @return 仲裁结果
     */
    public ArbitrationResult arbitrate(List<AgentVote> results) {
        if (results.isEmpty()) {
            return new ArbitrationResult(null, ArbitrationStrategy.NO_RESULT, false);
        }
        if (results.size() == 1) {
            return new ArbitrationResult(results.getFirst().getContent(), ArbitrationStrategy.SINGLE, false);
        }

        // 按内容分组（简单相似度：完全相同）
        var groups = results.stream().collect(Collectors.groupingBy(AgentVote::getContent));

        // 多数一致
        var majority = groups.entrySet().stream()
                .max(Map.Entry.comparingByValue((a, b) -> Integer.compare(a.size(), b.size())))
                .orElse(null);

        if (majority != null && majority.getValue().size() > results.size() / 2) {
            return new ArbitrationResult(majority.getKey(), ArbitrationStrategy.MAJORITY_VOTE, false);
        }

        // 按置信度排序
        var sorted = results.stream()
                .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                .toList();

        var best = sorted.getFirst();
        var second = sorted.get(1);

        // 置信度差距大，取最高
        if (best.getConfidence() - second.getConfidence() > CONFLICT_THRESHOLD) {
            return new ArbitrationResult(best.getContent(), ArbitrationStrategy.HIGHEST_CONFIDENCE, false);
        }

        // 冲突无法自动解决，升级到人工
        return new ArbitrationResult(best.getContent(), ArbitrationStrategy.HUMAN_ESCALATION, true);
    }

    /** Agent 投票 */
    @Getter
    @Setter
    public static class AgentVote {
        private String agentId;
        private String content;
        private double confidence;

        public AgentVote(String agentId, String content, double confidence) {
            this.agentId = agentId;
            this.content = content;
            this.confidence = confidence;
        }
    }

    /** 仲裁结果 */
    public record ArbitrationResult(String content, ArbitrationStrategy strategy, boolean needsHumanReview) {}

    /** 仲裁策略 */
    public enum ArbitrationStrategy {
        SINGLE,              // 只有一个结果
        MAJORITY_VOTE,       // 多数投票
        HIGHEST_CONFIDENCE,  // 最高置信度
        HUMAN_ESCALATION,    // 人工升级
        NO_RESULT            // 无结果
    }
}
