package com.xuejiai.aaf.framework.intelligent.agent.port;

import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import reactor.core.publisher.Mono;

/** AAF 工具治理与实际调用的稳定边界。 */
public interface ToolInvocationPort {

    /** 调用已经过目录解析的工具。 */
    Mono<ToolInvocationResult> invoke(ToolInvocation invocation);

    /** 单次工具调用。 */
    record ToolInvocation(
            String toolCallId,
            ToolRef tool,
            Map<String, Object> arguments,
            InvocationContext context) {

        public ToolInvocation {
            Objects.requireNonNull(toolCallId, "toolCallId 不能为空");
            Objects.requireNonNull(tool, "tool 不能为空");
            arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments 不能为空"));
            Objects.requireNonNull(context, "context 不能为空");
        }
    }

    /** 已适合返回模型的工具结果。 */
    record ToolInvocationResult(String output, Map<String, Object> metadata) {

        public ToolInvocationResult {
            Objects.requireNonNull(output, "output 不能为空");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
