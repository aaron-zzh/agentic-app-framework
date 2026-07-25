package com.xuejiai.aaf.framework.intelligent.shared.event;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** ai_task_event 的 append-only 唯一事实端口，同时支持 SSE 断点续读。 */
public interface ExecutionEventStorePort {

    Mono<ExecutionEvent> append(ExecutionEvent event);

    Flux<StoredExecutionEvent> readTask(TenantId tenantId, TaskId taskId, long afterEventOffset);

    Flux<ExecutionEvent> readExecution(
            TenantId tenantId, ExecutionId executionId, long afterSequence);

    Mono<Long> nextSequence(TenantId tenantId, ExecutionId executionId);

    record StoredExecutionEvent(long eventOffset, ExecutionEvent event) {
        public StoredExecutionEvent {
            if (eventOffset < 1) {
                throw new IllegalArgumentException("eventOffset 必须大于 0");
            }
            if (event == null) {
                throw new IllegalArgumentException("event 不能为空");
            }
        }
    }
}
