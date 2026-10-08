package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.List;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;

/** AIGC Tool 动作与专业能力适配器之间的稳定运行端口。 */
public interface AigcToolExecutionPort {

    /** 该端口是否负责执行绑定中的 Tool 目标引用；同一 targetRef 只允许一个端口声明支持。 */
    boolean supports(String targetRef);

    ToolResult execute(Command command);

    record ToolResult(String output, List<Long> taskIds) {

        public ToolResult {
            output = output == null ? "" : output;
            taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
        }

        public boolean asynchronous() {
            return !taskIds.isEmpty();
        }
    }
}
