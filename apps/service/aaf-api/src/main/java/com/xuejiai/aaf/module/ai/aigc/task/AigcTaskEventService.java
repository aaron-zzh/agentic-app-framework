package com.xuejiai.aaf.module.ai.aigc.task;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * AIGC 任务 SSE 推送服务——按 userId 维度订阅，任务状态变更时实时推送给对应用户的所有连接。
 *
 * <p>事件类型：task.created / task.progress / task.completed / task.failed
 *
 * @author Kiro
 */
@Slf4j
@Service
public class AigcTaskEventService {

    private static final long SSE_TIMEOUT = 10 * 60 * 1000L;

    /** userId → 订阅者连接集合 */
    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /**
     * 用户订阅 AIGC 任务事件流。
     *
     * @param userId 用户 ID
     * @return SseEmitter
     */
    public SseEmitter subscribe(Long userId) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        subscribers.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        // 立即发送心跳，防止 Tomcat 异步超时关闭连接
        try {
            emitter.send(SseEmitter.event().name("heartbeat").data("connected"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    /**
     * 推送事件给指定用户的所有连接。
     *
     * @param userId 目标用户 ID
     * @param eventType 事件类型（task.created / task.progress / task.completed / task.failed）
     * @param data 事件数据（通常为 AigcTaskVO）
     */
    public void push(Long userId, String eventType, Object data) {
        var emitters = subscribers.get(userId);
        if (emitters == null || emitters.isEmpty()) return;

        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(data));
            } catch (IOException e) {
                removeEmitter(userId, emitter);
            }
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        var set = subscribers.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) subscribers.remove(userId);
        }
    }
}
