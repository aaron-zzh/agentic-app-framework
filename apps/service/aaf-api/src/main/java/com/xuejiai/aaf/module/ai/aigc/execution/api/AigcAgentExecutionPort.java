package com.xuejiai.aaf.module.ai.aigc.execution.api;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Submission;

/** AIGC 动作提交 Agent runtime 的稳定边界。 */
public interface AigcAgentExecutionPort {

    Submission submit(Command command);
}
