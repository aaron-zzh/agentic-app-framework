package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.InputClassifier;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 非阻塞任务输入入口；accept 只持久输入事实并唤醒调度，不抢占 conversation lease。
 *
 * <p>写入前调用 {@link InputClassifier} 重新判定 {@code kind}，不直接信任客户端建议值
 * （方案 C，2026-08-30 拍板）；分类器未装配时保留客户端建议值作降级，不阻断输入接收。
 */
public final class TaskIngress {
    private final DelegatedTaskPort tasks;
    private final DelegatedTaskDispatchPort dispatch;
    private final TaskBoardPort boards;
    private final InputClassifier classifier;

    public TaskIngress(DelegatedTaskPort tasks, DelegatedTaskDispatchPort dispatch) {
        this(tasks, dispatch, null, null);
    }

    public TaskIngress(
            DelegatedTaskPort tasks,
            DelegatedTaskDispatchPort dispatch,
            TaskBoardPort boards,
            InputClassifier classifier) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch 不能为空");
        this.boards = boards;
        this.classifier = classifier;
    }

    public Mono<Acceptance> accept(ExecutionInput input) {
        Objects.requireNonNull(input, "input 不能为空");
        return Mono.fromCallable(() -> tasks.bufferInput(classified(input)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(
                        accepted -> {
                            if (accepted.created()) {
                                dispatch.signal(input.tenantId(), input.taskId());
                            }
                        })
                .map(accepted -> new Acceptance(accepted.input(), accepted.created()));
    }

    /** 分类器与 Board 端口均装配、且输入携带原始文本时才重新判定；否则原样保留客户端建议值。 */
    private ExecutionInput classified(ExecutionInput input) {
        if (classifier == null || boards == null || input.text() == null) {
            return input;
        }
        var taskContext =
                boards.find(input.tenantId(), input.taskId())
                        .map(board -> board.goal().description())
                        .orElse("");
        var kind = classifier.classify(input.text(), taskContext, input.userId());
        if (kind == input.kind()) {
            return input;
        }
        return new ExecutionInput(
                input.inputId(),
                input.tenantId(),
                input.userId(),
                input.taskId(),
                kind,
                input.text(),
                input.values(),
                input.receivedAt());
    }

    public record Acceptance(ExecutionInput input, boolean created) {
        public Acceptance {
            Objects.requireNonNull(input, "input 不能为空");
        }
    }
}
