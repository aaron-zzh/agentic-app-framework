package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/** Agent 可见工具定义的目录端口。 */
public interface ToolCatalogPort {

    /** 按精确引用解析工具，返回顺序应与输入一致。 */
    List<ToolDefinition> resolve(List<ToolRef> tools);

    /** 提供给模型的工具 schema，不包含凭证和运行态参数。 */
    record ToolDefinition(
            ToolRef ref,
            String description,
            Map<String, Object> inputSchema,
            boolean readOnly) {

        public ToolDefinition {
            Objects.requireNonNull(ref, "ref 不能为空");
            Objects.requireNonNull(description, "description 不能为空");
            inputSchema = Map.copyOf(Objects.requireNonNull(inputSchema, "inputSchema 不能为空"));
        }
    }
}
