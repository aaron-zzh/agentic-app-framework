package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 非阻塞任务输入入口；kind 由具体 canonical API 决定，入口只持久输入事实，不抢占 conversation lease。 */
public final class TaskIngress {
    private final TaskUnitOfWork tasks;

    public TaskIngress(TaskUnitOfWork tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    public Mono<Acceptance> accept(ExecutionInput input) {
        Objects.requireNonNull(input, "input 不能为空");
        return Mono.fromCallable(() -> tasks.bufferInput(input))
                .subscribeOn(Schedulers.boundedElastic())
                .map(accepted -> new Acceptance(accepted.input(), accepted.created()));
    }

    public record Acceptance(ExecutionInput input, boolean created) {
        public Acceptance {
            Objects.requireNonNull(input, "input 不能为空");
        }
    }
}
