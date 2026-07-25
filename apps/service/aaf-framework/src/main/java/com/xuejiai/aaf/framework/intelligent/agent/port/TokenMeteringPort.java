package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;

/** 从真实模型结束事件写入的幂等用量事实边界。 */
public interface TokenMeteringPort {

    MeteringResult record(ModelUsageFact fact);

    record ModelUsageFact(
            String usageId,
            InvocationContext context,
            String modelId,
            String capability,
            long inputTokens,
            long outputTokens,
            long cachedTokens,
            Instant occurredAt) {
        public ModelUsageFact {
            Objects.requireNonNull(usageId, "usageId 不能为空");
            Objects.requireNonNull(context, "context 不能为空");
            Objects.requireNonNull(modelId, "modelId 不能为空");
            Objects.requireNonNull(capability, "capability 不能为空");
            Objects.requireNonNull(occurredAt, "occurredAt 不能为空");
            if (inputTokens < 0 || outputTokens < 0 || cachedTokens < 0) {
                throw new IllegalArgumentException("Token 用量不能为负数");
            }
            if (cachedTokens > inputTokens) {
                throw new IllegalArgumentException("cachedTokens 不能超过 inputTokens");
            }
        }
    }

    record MeteringResult(String usageId, boolean created) {}
}
