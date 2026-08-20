package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 非阻塞任务输入入口；accept 只持久输入事实并唤醒调度，不抢占 conversation lease。 */
public final class TaskIngress {
    private final DelegatedTaskPort tasks;
    private final DelegatedTaskDispatchPort dispatch;

    public TaskIngress(DelegatedTaskPort tasks, DelegatedTaskDispatchPort dispatch) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch 不能为空");
    }

    public Mono<Acceptance> accept(ExecutionInput input) {
        Objects.requireNonNull(input, "input 不能为空");
        return Mono.fromCallable(() -> tasks.bufferInput(input))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(
                        accepted -> {
                            if (accepted.created()) {
                                dispatch.signal(input.tenantId(), input.taskId());
                            }
                        })
                .map(accepted -> new Acceptance(accepted.input(), accepted.created()));
    }

    public record Acceptance(ExecutionInput input, boolean created) {
        public Acceptance {
            Objects.requireNonNull(input, "input 不能为空");
        }
    }
}
