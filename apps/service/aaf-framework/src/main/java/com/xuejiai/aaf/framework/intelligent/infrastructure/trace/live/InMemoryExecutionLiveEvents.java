package com.xuejiai.aaf.framework.intelligent.infrastructure.trace.live;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionLiveEventPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * 进程内实时事件分发实现，仅适用于执行与入口在同一 JVM 的单实例部署。
 *
 * <p>多实例部署需要换成跨进程通道（例如 Redis Streams），端口契约不变。
 */
public final class InMemoryExecutionLiveEvents implements ExecutionLiveEventPort {

    /** 登记后一直无人订阅的孤儿订阅的回收时限，防止调用方在订阅前失败造成泄漏。 */
    private static final Duration ORPHAN_TTL = Duration.ofMinutes(15);

    private final ConcurrentMap<String, Set<Sinks.Many<ExecutionEvent>>> subscriptions =
            new ConcurrentHashMap<>();

    @Override
    public void publish(ExecutionEvent event) {
        if (event.taskId() == null) {
            return;
        }
        var sinks = subscriptions.get(key(event.tenantId(), event.taskId()));
        if (sinks == null) {
            return;
        }
        for (var sink : sinks) {
            synchronized (sink) {
                sink.tryEmitNext(event);
            }
        }
    }

    @Override
    public Flux<ExecutionEvent> open(TenantId tenantId, TaskId taskId) {
        var key = key(tenantId, taskId);
        var sink = Sinks.many().unicast().<ExecutionEvent>onBackpressureBuffer();
        subscriptions.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(sink);
        Disposable orphanReaper =
                Schedulers.parallel()
                        .schedule(
                                () -> {
                                    remove(key, sink);
                                    synchronized (sink) {
                                        sink.tryEmitComplete();
                                    }
                                },
                                ORPHAN_TTL.toMillis(),
                                java.util.concurrent.TimeUnit.MILLISECONDS);
        return sink.asFlux()
                // 一旦真实订阅建立就不再是孤儿，取消回收计时器，避免活跃订阅满时限被强制结束
                .doOnSubscribe(ignored -> orphanReaper.dispose())
                .doFinally(
                        ignored -> {
                            orphanReaper.dispose();
                            remove(key, sink);
                        });
    }

    private void remove(String key, Sinks.Many<ExecutionEvent> sink) {
        subscriptions.computeIfPresent(
                key,
                (ignored, sinks) -> {
                    sinks.remove(sink);
                    return sinks.isEmpty() ? null : sinks;
                });
    }

    private static String key(TenantId tenantId, TaskId taskId) {
        return tenantId.value() + '|' + taskId.value();
    }
}
