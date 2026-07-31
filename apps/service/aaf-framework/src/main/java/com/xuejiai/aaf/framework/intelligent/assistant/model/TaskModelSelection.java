package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;

/** Dynamic 子任务执行模型的选择方式；不影响 Assistant 编排模型或 Predefined Agent。 */
public record TaskModelSelection(Mode mode, String modelId) {

    public TaskModelSelection {
        Objects.requireNonNull(mode, "mode 不能为空");
        if (mode == Mode.EXPLICIT) {
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("EXPLICIT 必须指定 modelId");
            }
            modelId = modelId.trim();
        } else if (modelId != null) {
            throw new IllegalArgumentException("AUTO 不允许指定 modelId");
        }
    }

    public static TaskModelSelection auto() {
        return new TaskModelSelection(Mode.AUTO, null);
    }

    public static TaskModelSelection explicit(String modelId) {
        return new TaskModelSelection(Mode.EXPLICIT, modelId);
    }

    public enum Mode {
        AUTO,
        EXPLICIT
    }
}
