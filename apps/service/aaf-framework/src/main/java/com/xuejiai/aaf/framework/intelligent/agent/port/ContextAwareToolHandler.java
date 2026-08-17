package com.xuejiai.aaf.framework.intelligent.agent.port;

import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;

/** 需要 InvocationContext 的内置工具处理器。 */
public interface ContextAwareToolHandler {

    String toolName();

    String description();

    Mono<ToolInvocationResult> invoke(ToolInvocation invocation);
}
