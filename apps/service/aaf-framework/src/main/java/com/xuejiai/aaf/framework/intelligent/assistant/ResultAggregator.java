package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.List;

/** 多 Agent 结果合并的纯计算对象，不参与 P3 单 Assistant 运行链。 */
public final class ResultAggregator {

    public AggregatedResult aggregate(List<AgentResult> results) {
        if (results.isEmpty()) return new AggregatedResult("无结果", 0.0);
        if (results.size() == 1) {
            var result = results.getFirst();
            return new AggregatedResult(result.content(), result.confidence());
        }
        var sorted = results.stream()
                .sorted((left, right) -> Double.compare(right.confidence(), left.confidence()))
                .toList();
        var best = sorted.getFirst();
        var second = sorted.get(1);
        if (best.confidence() - second.confidence() < 0.1
                && !best.content().equals(second.content())) {
            return new AggregatedResult(
                    best.content() + "\n\n[注：存在不同观点] " + second.content(),
                    best.confidence() * 0.9);
        }
        return new AggregatedResult(best.content(), best.confidence());
    }

    public record AgentResult(String agentId, String content, double confidence) {}

    public record AggregatedResult(String content, double confidence) {}
}
