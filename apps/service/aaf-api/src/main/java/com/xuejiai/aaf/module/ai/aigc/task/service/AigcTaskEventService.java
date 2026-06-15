package com.xuejiai.aaf.module.ai.aigc.task.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.messaging.sse.SseSessionManager;

import lombok.RequiredArgsConstructor;

/**
 * AIGC 任务 SSE 推送服务——委托通用 {@link SseSessionManager} 管理连接和心跳。
 *
 * <p>事件类型：task.created / task.progress / task.completed / task.failed
 *
 * @author AaronZZH
 */
@Service
@RequiredArgsConstructor
public class AigcTaskEventService {

    private final SseSessionManager sseSessionManager;

    /** 用户订阅 AIGC 任务事件流。 */
    public SseEmitter subscribe(Long userId) {
        return sseSessionManager.subscribe(userId);
    }

    /**
     * 推送事件给指定用户。
     *
     * @param userId 目标用户 ID
     * @param eventType 事件类型（task.created / task.completed / task.failed）
     * @param data 事件数据
     */
    public void push(Long userId, String eventType, Object data) {
        sseSessionManager.push(userId, eventType, data);
    }
}
