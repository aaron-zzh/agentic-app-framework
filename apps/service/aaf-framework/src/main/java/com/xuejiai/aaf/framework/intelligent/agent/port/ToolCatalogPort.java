package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/** Agent 可见工具定义的目录端口。 */
public interface ToolCatalogPort {

    List<ToolDefinition> resolve(List<ToolRef> tools);

    record ToolDefinition(
            ToolRef ref,
            String description,
            Map<String, Object> inputSchema,
            String source,
            String permissionCode,
            boolean readOnly,
            boolean reversible,
            boolean requireConfirm,
            boolean idempotencyRequired) {

        public ToolDefinition {
            Objects.requireNonNull(ref, "ref 不能为空");
            Objects.requireNonNull(description, "description 不能为空");
            inputSchema = Map.copyOf(Objects.requireNonNull(inputSchema, "inputSchema 不能为空"));
            source = Objects.requireNonNullElse(source, "LOCAL").trim();
            permissionCode = Objects.requireNonNullElse(permissionCode, "").trim();
            if (readOnly && reversible) {
                throw new IllegalArgumentException("只读工具不能标记为可撤销写入");
            }
            var connector = "MCP".equalsIgnoreCase(source) || "CONNECTOR".equalsIgnoreCase(source);
            if (connector && !readOnly && !idempotencyRequired) {
                throw new IllegalArgumentException("Connector 写动作必须声明服务端幂等键");
            }
            if (idempotencyRequired && (!connector || readOnly)) {
                throw new IllegalArgumentException("幂等声明仅适用于 Connector 写动作");
            }
        }

        public boolean connectorAction() {
            return "MCP".equalsIgnoreCase(source) || "CONNECTOR".equalsIgnoreCase(source);
        }
    }
}
