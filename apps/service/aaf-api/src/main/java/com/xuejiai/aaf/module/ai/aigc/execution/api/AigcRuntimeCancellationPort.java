package com.xuejiai.aaf.module.ai.aigc.execution.api;

/** AIGC 业务执行取消底层 Agent/Workflow runtime 的稳定边界。 */
public interface AigcRuntimeCancellationPort {

    void cancel(String targetType, String runtimeTraceId, String reason);
}
