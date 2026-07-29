package com.xuejiai.aaf.module.ai.assistant.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.module.ai.assistant.vo.DelegatedTaskEventVO;

/** 委托任务执行事件 SSE 推送服务。 */
@Service
public class DelegatedTaskEventStreamService {

    private static final long SSE_TIMEOUT = 10 * 60 * 1000L;

    private final Map<SubscriptionKey, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(TenantId tenantId, TaskId taskId) {
        var key = new SubscriptionKey(tenantId.value(), taskId.value());
        var emitter = new SseEmitter(SSE_TIMEOUT);
        subscribers.computeIfAbsent(key, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(ignored -> remove(key, emitter));
        return emitter;
    }

    @EventListener
    public void onExecutionEventAppended(StoredExecutionEvent stored) {
        var event = stored.event();
        var key = new SubscriptionKey(event.tenantId().value(), event.taskId().value());
        var emitters = subscribers.get(key);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        var eventVO = DelegatedTaskEventVO.from(stored);
        for (var emitter : emitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .id(Long.toString(stored.eventOffset()))
                                .name(event.type().name())
                                .data(eventVO, MediaType.APPLICATION_JSON));
            } catch (IOException failure) {
                remove(key, emitter);
            }
        }
    }

    private void remove(SubscriptionKey key, SseEmitter emitter) {
        var emitters = subscribers.get(key);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(key, emitters);
        }
    }

    private record SubscriptionKey(String tenantId, String taskId) {}
}
