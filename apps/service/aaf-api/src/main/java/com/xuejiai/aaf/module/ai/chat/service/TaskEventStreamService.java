package com.xuejiai.aaf.module.ai.chat.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.module.ai.chat.domain.TaskEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务事件 SSE 推送服务——前端实时订阅任务执行状态。
 *
 * <p>DurableTaskExecutor 每次 emitEvent 后调用 broadcast 推送给所有订阅者。
 */
@Slf4j
@Service
public class TaskEventStreamService {

    private static final long SSE_TIMEOUT = 10 * 60 * 1000L;

    /** taskId → 订阅者集合 */
    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /** 订阅任务事件流 */
    public SseEmitter subscribe(Long taskId) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        subscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        emitter.onError(e -> removeEmitter(taskId, emitter));

        return emitter;
    }

    /** 广播事件给所有订阅该任务的客户端 */
    public void broadcast(TaskEvent event) {
        var emitters = subscribers.get(event.getTaskId());
        if (emitters == null || emitters.isEmpty()) return;

        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event.getType()).data(event));
            } catch (IOException e) {
                removeEmitter(event.getTaskId(), emitter);
            }
        }
    }

    private void removeEmitter(Long taskId, SseEmitter emitter) {
        var set = subscribers.get(taskId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) subscribers.remove(taskId);
        }
    }
}
