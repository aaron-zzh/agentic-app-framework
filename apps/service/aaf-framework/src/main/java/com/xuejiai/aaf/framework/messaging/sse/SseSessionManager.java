package com.xuejiai.aaf.framework.messaging.sse;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * 通用 SSE 会话管理器——按 userId 维护 SSE 长连接，支持向指定用户推送事件。
 *
 * <p>一个用户可同时存在多个连接（多 tab），消息广播给该用户的所有活跃连接。 统一管理心跳保活和连接清理，业务模块无需自行维护 emitter 生命周期。
 *
 * <p>用法：
 *
 * <pre>
 * // 1. 前端订阅（Controller 层）
 * SseEmitter emitter = sseSessionManager.subscribe(userId);
 *
 * // 2. 后端推送事件
 * sseSessionManager.push(userId, "task.completed", taskVO);
 * </pre>
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
public class SseSessionManager {

    /** 永不超时，由心跳保活 */
    private static final long SSE_TIMEOUT = -1L;

    /** userId → 订阅者连接集合（一用户多 tab） */
    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /**
     * 用户订阅 SSE 事件流。
     *
     * @param userId 用户 ID
     * @return SseEmitter（由 Controller 直接返回给前端）
     */
    public SseEmitter subscribe(Long userId) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        subscribers.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        // 立即发送心跳，防止 Tomcat 异步超时关闭连接
        try {
            emitter.send(SseEmitter.event().name("heartbeat").data("connected"));
        } catch (Exception e) {
            remove(userId, emitter);
        }

        log.debug(
                "用户 {} SSE 已连接，当前连接数: {}",
                userId,
                subscribers.getOrDefault(userId, Set.of()).size());
        return emitter;
    }

    /**
     * 向指定用户的所有活跃连接推送事件。
     *
     * @param userId 目标用户 ID
     * @param eventName 事件名称（如 task.completed / notify / progress）
     * @param data 事件数据（自动序列化为 JSON）
     */
    public void push(Long userId, String eventName, Object data) {
        var emitters = subscribers.get(userId);
        if (emitters == null || emitters.isEmpty()) return;

        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                log.debug("SSE 推送失败，移除失效连接: userId={}, event={}", userId, eventName);
                remove(userId, emitter);
            }
        }
    }

    /** 是否有活跃连接 */
    public boolean hasSubscriber(Long userId) {
        var emitters = subscribers.get(userId);
        return emitters != null && !emitters.isEmpty();
    }

    /** 当前在线用户数 */
    public int onlineCount() {
        return subscribers.size();
    }

    private void remove(Long userId, SseEmitter emitter) {
        var set = subscribers.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) subscribers.remove(userId);
        }
    }

    /** 每 15 秒向所有活跃连接发送心跳，防止 CDN/代理 30 秒无数据断连 */
    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        subscribers.forEach(
                (userId, emitters) -> {
                    for (var emitter : emitters) {
                        try {
                            emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                        } catch (Exception e) {
                            remove(userId, emitter);
                        }
                    }
                });
    }
}
