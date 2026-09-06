package com.xuejiai.aaf.module.ai.aigc.event.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.ai.aigc.event.api.AigcActivityEnvelope;
import com.xuejiai.aaf.module.ai.aigc.event.api.SubscriptionScope;
import com.xuejiai.aaf.module.ai.aigc.event.domain.AigcActivityEvent;
import com.xuejiai.aaf.module.ai.aigc.event.repository.AigcActivityEventRepository;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskView;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/** DB 为真理源的 Activity 事件发布、游标重放与 live SSE。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigcActivityEventService {

    private static final String WIRE_EVENT = "activity";
    private static final long SSE_TIMEOUT = -1L;
    private static final int REPLAY_LIMIT = 500;

    private final AigcActivityEventRepository repository;
    private final Map<SubscriptionScope, Set<LiveSubscriber>> subscribers =
            new ConcurrentHashMap<>();
    private final Map<SubscriptionScope, ReentrantLock> scopeLocks = new ConcurrentHashMap<>();

    @Transactional
    public AigcActivityEvent publish(
            Long userId,
            String eventType,
            Long projectId,
            Long executionRunId,
            Long taskId,
            Long objectVersionId,
            Long reviewId,
            Long workId,
            Long publicationId,
            Object data) {
        var event = new AigcActivityEvent();
        event.setOwnerId(userId);
        event.setOrgId(OrgContext.getCurrentOrgId());
        event.setWorkspaceId(OrgContext.getCurrentWorkspaceId());
        event.setEventType(eventType);
        event.setProjectId(projectId);
        event.setExecutionRunId(executionRunId);
        event.setTaskId(taskId);
        event.setObjectVersionId(objectVersionId);
        event.setReviewId(reviewId);
        event.setWorkId(workId);
        event.setPublicationId(publicationId);
        event.setPayload(payload(data));
        lockScope(event);
        repository.saveAndFlush(event);
        afterCommit(() -> broadcast(event));
        return event;
    }

    @Transactional
    public AigcActivityEvent publish(Long userId, String eventType, Object data) {
        var event = new AigcActivityEvent();
        event.setOwnerId(userId);
        event.setOrgId(OrgContext.getCurrentOrgId());
        event.setWorkspaceId(OrgContext.getCurrentWorkspaceId());
        event.setEventType(eventType);
        event.setPayload(payload(data));
        if (data instanceof AigcTaskVO task) {
            event.setTaskId(task.id());
            event.setProjectId(task.projectId());
            event.setExecutionRunId(task.executionRunId());
            event.setMediaVersionId(task.outputMediaVersionId());
        } else if (data instanceof AigcTaskView task) {
            event.setTaskId(task.id());
            event.setExecutionRunId(task.executionRunId());
            event.setMediaVersionId(task.outputMediaVersionId());
        }
        lockScope(event);
        repository.saveAndFlush(event);
        afterCommit(() -> broadcast(event));
        return event;
    }

    public SseEmitter subscribe(SubscriptionScope scope, long afterId) {
        return subscribe(scope, afterId, new SseEmitter(SSE_TIMEOUT));
    }

    SseEmitter subscribe(SubscriptionScope scope, long afterId, SseEmitter emitter) {
        var scopeLock = scopeLocks.computeIfAbsent(scope, ignored -> new ReentrantLock());
        scopeLock.lock();
        try {
            var upperBound =
                    repository.replayUpperBound(
                            scope.ownerId(), scope.orgId(), scope.workspaceId());
            var cursor = Math.min(afterId, upperBound);
            while (cursor < upperBound) {
                var events =
                        repository.replay(
                                scope.ownerId(),
                                scope.orgId(),
                                scope.workspaceId(),
                                cursor,
                                upperBound,
                                PageRequest.of(0, REPLAY_LIMIT));
                for (var event : events) {
                    send(emitter, event);
                    cursor = event.getId();
                }
                if (events.size() < REPLAY_LIMIT) {
                    break;
                }
            }
            var subscriber = new LiveSubscriber(scope, emitter, upperBound);
            subscriber.registerCallbacks();
            subscribers
                    .computeIfAbsent(scope, ignored -> ConcurrentHashMap.newKeySet())
                    .add(subscriber);
            subscriber.comment("connected");
        } catch (IOException error) {
            emitter.completeWithError(error);
        } finally {
            scopeLock.unlock();
        }
        return emitter;
    }

    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        subscribers.forEach(
                (scope, liveSubscribers) -> {
                    var scopeLock =
                            scopeLocks.computeIfAbsent(scope, ignored -> new ReentrantLock());
                    scopeLock.lock();
                    try {
                        var upperBound =
                                repository.replayUpperBound(
                                        scope.ownerId(), scope.orgId(), scope.workspaceId());
                        liveSubscribers.forEach(
                                subscriber -> {
                                    subscriber.catchUp(upperBound);
                                    if (liveSubscribers.contains(subscriber)) {
                                        subscriber.comment("heartbeat");
                                    }
                                });
                    } finally {
                        scopeLock.unlock();
                    }
                });
    }

    void broadcast(AigcActivityEvent event) {
        var scope =
                new SubscriptionScope(event.getOwnerId(), event.getOrgId(), event.getWorkspaceId());
        var scopeLock = scopeLocks.computeIfAbsent(scope, ignored -> new ReentrantLock());
        scopeLock.lock();
        try {
            var liveSubscribers = subscribers.get(scope);
            if (liveSubscribers == null || liveSubscribers.isEmpty()) {
                return;
            }
            var upperBound =
                    repository.replayUpperBound(
                            scope.ownerId(), scope.orgId(), scope.workspaceId());
            liveSubscribers.forEach(subscriber -> subscriber.catchUp(upperBound));
        } finally {
            scopeLock.unlock();
        }
    }

    private void lockScope(AigcActivityEvent event) {
        repository.lockScope(event.getOwnerId(), event.getOrgId(), event.getWorkspaceId());
    }

    private void send(SseEmitter emitter, AigcActivityEvent event) throws IOException {
        var envelope =
                new AigcActivityEnvelope(
                        event.getId(),
                        event.getEventType(),
                        event.getProjectId(),
                        event.getExecutionRunId(),
                        event.getTaskId(),
                        event.getMediaVersionId(),
                        event.getObjectVersionId(),
                        event.getReviewId(),
                        event.getWorkId(),
                        event.getPublicationId(),
                        event.getConversationId(),
                        event.getPayload());
        emitter.send(
                SseEmitter.event()
                        .id(String.valueOf(event.getId()))
                        .name(WIRE_EVENT)
                        .data(envelope));
    }

    private Map<String, Object> payload(Object data) {
        if (data == null) {
            return Map.of();
        }
        var json = JsonUtils.toJsonString(data);
        try {
            return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException error) {
            return Map.of("data", String.valueOf(data));
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }

    private void remove(LiveSubscriber subscriber) {
        var liveSubscribers = subscribers.get(subscriber.scope);
        if (liveSubscribers == null) {
            return;
        }
        liveSubscribers.remove(subscriber);
        if (liveSubscribers.isEmpty()) {
            subscribers.remove(subscriber.scope, liveSubscribers);
        }
    }

    int subscriberCount(SubscriptionScope scope) {
        var liveSubscribers = subscribers.get(scope);
        return liveSubscribers == null ? 0 : liveSubscribers.size();
    }

    private final class LiveSubscriber {

        private final SubscriptionScope scope;
        private final SseEmitter emitter;
        private final ReentrantLock sendLock = new ReentrantLock();
        private long cursor;

        private LiveSubscriber(SubscriptionScope scope, SseEmitter emitter, long cursor) {
            this.scope = scope;
            this.emitter = emitter;
            this.cursor = cursor;
        }

        private void registerCallbacks() {
            emitter.onCompletion(() -> remove(this));
            emitter.onTimeout(() -> remove(this));
            emitter.onError(error -> remove(this));
        }

        private void catchUp(long upperBound) {
            sendLock.lock();
            try {
                while (cursor < upperBound) {
                    var events =
                            repository.replay(
                                    scope.ownerId(),
                                    scope.orgId(),
                                    scope.workspaceId(),
                                    cursor,
                                    upperBound,
                                    PageRequest.of(0, REPLAY_LIMIT));
                    if (events.isEmpty()) {
                        return;
                    }
                    for (var event : events) {
                        if (event.getId() <= cursor) {
                            continue;
                        }
                        send(emitter, event);
                        cursor = event.getId();
                    }
                    if (events.size() < REPLAY_LIMIT) {
                        return;
                    }
                }
            } catch (IOException error) {
                remove(this);
                emitter.completeWithError(error);
            } finally {
                sendLock.unlock();
            }
        }

        private void comment(String value) {
            try {
                sendLock.lock();
                try {
                    emitter.send(SseEmitter.event().comment(value));
                } finally {
                    sendLock.unlock();
                }
            } catch (IOException error) {
                remove(this);
                emitter.completeWithError(error);
            }
        }
    }
}
