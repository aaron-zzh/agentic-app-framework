package com.xuejiai.aaf.module.ai.event;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.ExecutionEventPublicMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/** Headless 唯一公开投影；正文、提示词、工具参数和异常详情不会跨越边界。 */
@Component
@Slf4j
public final class HeadlessProjector {

    private final ExecutionEventPublicMapper mapper;

    public HeadlessProjector(ExecutionEventPublicMapper mapper) {
        this.mapper = mapper;
    }

    public Flux<AafAiTaskEvent> project(Flux<ExecutionEvent> source, String executionId) {
        var lastSequence = new AtomicLong();
        var lastStatus = new AtomicReference<>("DRAFT");
        var lastEvent = new AtomicReference<AafAiTaskEvent>();
        var terminal = new AtomicBoolean();
        return source.map(
                        event -> {
                            lastSequence.accumulateAndGet(event.sequence(), Math::max);
                            var projected = mapper.map(event);
                            lastEvent.set(projected);
                            lastStatus.set(projected.status());
                            if (projected.terminal()) {
                                terminal.set(true);
                            }
                            return projected;
                        })
                .takeUntil(AafAiTaskEvent::terminal)
                .onErrorResume(
                        failure -> {
                            if (terminal.get()) {
                                return Flux.empty();
                            }
                            terminal.set(true);
                            var errorCode =
                                    failure instanceof ProjectionFailure projectionFailure
                                            ? projectionFailure.errorCode()
                                            : "ASSISTANT_PUBLIC_PROJECTION_FAILED";
                            log.debug(
                                    "[HeadlessProjector] executionId={} sequence={} failureType={} errorCode={}",
                                    executionId,
                                    lastSequence.get(),
                                    failure.getClass().getName(),
                                    errorCode);
                            return Flux.just(
                                    mapper.projectionFailure(
                                            lastEvent.get(),
                                            executionId,
                                            lastSequence.incrementAndGet(),
                                            errorCode));
                        })
                .concatWith(
                        Flux.defer(
                                () -> {
                                    if (terminal.get()) {
                                        return Flux.empty();
                                    }
                                    terminal.set(true);
                                    return Flux.just(
                                            mapper.projectionClosed(
                                                    lastEvent.get(),
                                                    executionId,
                                                    lastSequence.incrementAndGet(),
                                                    lastStatus.get()));
                                }));
    }

    public AafAiTaskEvent project(StoredExecutionEvent stored) {
        return mapper.map(stored);
    }

    public AafAiTaskEvent terminalFailure(String executionId, long sequence, String errorCode) {
        return mapper.projectionFailure(null, executionId, sequence, errorCode);
    }

    public static ProjectionFailure failure(String errorCode) {
        return new ProjectionFailure(errorCode);
    }

    public static final class ProjectionFailure extends RuntimeException {
        private final String errorCode;

        private ProjectionFailure(String errorCode) {
            super("公共事件投影失败");
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
