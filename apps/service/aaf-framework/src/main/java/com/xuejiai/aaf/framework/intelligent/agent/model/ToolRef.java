package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.Objects;

/** Agent 定义中对版本化工具的稳定引用。 */
public record ToolRef(String toolId, long version, String name) {

    public ToolRef {
        Objects.requireNonNull(toolId, "toolId 不能为空");
        Objects.requireNonNull(name, "name 不能为空");
        if (toolId.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("工具标识和名称不能为空白");
        }
        if (version < 1) {
            throw new IllegalArgumentException("工具版本必须大于 0");
        }
    }
}
