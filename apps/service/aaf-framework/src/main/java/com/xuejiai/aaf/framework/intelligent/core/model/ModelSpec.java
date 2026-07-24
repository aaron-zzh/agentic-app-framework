package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.Objects;

/** Agent 执行使用的稳定模型规格。 */
public record ModelSpec(String modelId) {

    public ModelSpec {
        Objects.requireNonNull(modelId, "modelId 不能为空");
        if (modelId.isBlank()) {
            throw new IllegalArgumentException("modelId 不能为空白");
        }
    }
}
