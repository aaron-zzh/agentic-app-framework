package com.xuejiai.aaf.framework.intelligent.assistant.port;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import reactor.core.publisher.Flux;

/** 交互层调用 Assistant 的唯一通用命令边界。 */
public interface AssistantCommandPort {

    Flux<ExecutionEvent> execute(AssistantCommand command);
}
