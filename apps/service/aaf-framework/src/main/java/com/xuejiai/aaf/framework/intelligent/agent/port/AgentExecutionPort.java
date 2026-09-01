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

    /**
     * 按 executionId 暂停当前活跃回合（AAF-110）。与 {@link #cancel} 的差异只在状态槎处置：责任主体不变，
     * 期待下次同一 {@code executionId} 重新发起时续接对话历史，因此不删除状态槎；{@code cancel} 是真正终态，
     * 会删除状态槎。中断下发逻辑与 {@code cancel} 一致。
     */
    Mono<Boolean> pause(ExecutionId executionId);
}
