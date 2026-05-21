/**
 * 结果聚合服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.List;

import org.springframework.stereotype.Service;

import io.agentscope.core.message.Msg;
import lombok.Getter;
import lombok.Setter;

/** 多 Agent 结果合并、冲突解决、置信度加权。 */
@Service
public class ResultAggregator {

    /** 聚合多个 Agent 的响应结果。 策略：按置信度加权合并，冲突时取最高置信度。 */
    public AggregatedResult aggregate(List<AgentResult> results) {
        if (results.isEmpty()) {
            return new AggregatedResult("无结果", 0.0);
        }
        if (results.size() == 1) {
            var r = results.getFirst();
            return new AggregatedResult(r.getContent(), r.getConfidence());
        }

        // 按置信度排序，取最高
        var sorted =
                results.stream()
                        .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                        .toList();

        var best = sorted.getFirst();

        // 检查冲突：如果前两名置信度接近但内容不同，标记冲突
        if (sorted.size() > 1) {
            var second = sorted.get(1);
            if (best.getConfidence() - second.getConfidence() < 0.1
                    && !best.getContent().equals(second.getContent())) {
                return new AggregatedResult(
                        best.getContent() + "\n\n[注：存在不同观点] " + second.getContent(),
                        best.getConfidence() * 0.9);
            }
        }

        return new AggregatedResult(best.getContent(), best.getConfidence());
    }

    /** 单个 Agent 结果 */
    @Getter
    @Setter
    public static class AgentResult {
        private String agentId;
        private String content;
        private double confidence;

        public AgentResult(String agentId, String content, double confidence) {
            this.agentId = agentId;
            this.content = content;
            this.confidence = confidence;
        }

        public static AgentResult from(String agentId, Msg msg) {
            return new AgentResult(agentId, msg.getTextContent(), 0.8);
        }
    }

    /** 聚合结果 */
    public record AggregatedResult(String content, double confidence) {}
}
