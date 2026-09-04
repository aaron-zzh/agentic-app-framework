package com.xuejiai.aaf.module.ai.aigc.execution.event;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;

/** ExecutionRun 提交后异步派发请求。 */
public record AigcExecutionRunDispatchRequestedEvent(Long runId, AigcActionCommand command) {}
