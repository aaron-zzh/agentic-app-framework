package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实体消歧分档配置（余弦距离，越小越相似）。
 *
 * <ul>
 *   <li>{@code candidateMaxDistance}：仅靠向量召回的候选需不超过该距离，否则不进入消歧；无候选直接新建实体
 *   <li>{@code autoLinkMaxDistance}：首个候选不超过该距离且类型一致时直接关联，不调用大模型
 * </ul>
 *
 * <p>默认值依据 qwen3.7-text-embedding 实测：同领域不同实体（如「服务层」与「交互层」）距离约 0.11～0.27，自动关联须明显低于该区间。
 */
@ConfigurationProperties(prefix = "aaf.knowledge.entity-resolution")
public record EntityResolutionProperties(double candidateMaxDistance, double autoLinkMaxDistance) {
    public EntityResolutionProperties {
        if (candidateMaxDistance <= 0) candidateMaxDistance = 0.2;
        if (autoLinkMaxDistance <= 0) autoLinkMaxDistance = 0.05;
        if (autoLinkMaxDistance > candidateMaxDistance) {
            throw new IllegalArgumentException("autoLinkMaxDistance 不能大于 candidateMaxDistance");
        }
    }
}
