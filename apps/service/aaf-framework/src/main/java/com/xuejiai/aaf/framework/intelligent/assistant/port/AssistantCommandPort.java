package com.xuejiai.aaf.framework.intelligent.assistant.port;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantInvocation;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;

import reactor.core.publisher.Flux;

/** 交互层调用 Assistant 的唯一通用命令边界。 */
public interface AssistantCommandPort {

    Flux<ExecutionEvent> invoke(AssistantInvocation invocation);

    default Flux<ExecutionEvent> execute(AssistantCommand command) {
        return invoke(AssistantInvocation.of(command));
    }
}
