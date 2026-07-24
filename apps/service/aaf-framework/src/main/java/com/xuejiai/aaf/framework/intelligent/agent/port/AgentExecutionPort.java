package com.xuejiai.aaf.framework.intelligent.agent.port;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Agent 的唯一流式执行边界。 */
public interface AgentExecutionPort {

    /** 执行或从同一状态槽位恢复一个 Agent 回合。 */
    Flux<ExecutionEvent> execute(AgentExecutionCommand command);

    /** 按 executionId 取消当前活跃回合。 */
    Mono<Boolean> cancel(ExecutionId executionId);
}
