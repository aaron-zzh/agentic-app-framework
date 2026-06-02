package com.xuejiai.aaf.framework.intelligent.assistant.hitl;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/** HITL 审批请求 SSE 推送服务。 */
@Slf4j
@Service
public class ApprovalEventStreamService implements ApprovalRequestPublisher {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final ConcurrentHashMap<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        subscribers.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ignored -> remove(userId, emitter));
        return emitter;
    }

    @Override
    public void publish(HumanApprovalService.ApprovalRequest request) {
        var emitters = subscribers.get(request.userId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("approval_request").data(request));
            } catch (IOException e) {
                log.debug("审批 SSE 推送失败，移除订阅: userId={}", request.userId(), e);
                remove(request.userId(), emitter);
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        var emitters = subscribers.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(userId);
        }
    }
}
