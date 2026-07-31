package com.xuejiai.aaf.framework.intelligent.agent.model;

/** Dynamic 子智能体的通用 CHAT 模型需求基线。 */
public record ModelSelectionRequirement(boolean reasoningRequired, boolean costSensitive) {

    public static ModelSelectionRequirement balanced() {
        return new ModelSelectionRequirement(false, false);
    }

    public static ModelSelectionRequirement reasoning() {
        return new ModelSelectionRequirement(true, false);
    }

    public static ModelSelectionRequirement costOptimized() {
        return new ModelSelectionRequirement(false, true);
    }
}
