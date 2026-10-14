package com.xuejiai.aaf.framework.messaging.event;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.framework.org.OrgContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局事件统一发布入口，业务模块只通过它向用户推送"变化通知"。
 *
 * <p>事件在调用方当前事务内落库（无事务时自行开启），提交后再广播，回滚的事件不会送达。事件只作失效通知，不携带权威业务数据，前端收到后重新查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysEventPublisher {

    private final SysEventRepository repository;
    private final SysEventStreamService streamService;

    /**
     * 发布事件，范围取调用线程的 {@link OrgContext}；无组织上下文（如工作线程）时为用户级事件。
     *
     * @param ownerId 接收事件的用户
     * @param type 事件类型，{域}.{资源}.{动作}，如 {@code aigc.task.completed}
     * @param refs 关联键值，值须为正整数 ID，null 或非正值会被丢弃
     * @param payload 状态摘要，null 值会被丢弃
     */
    @Transactional
    public SysEvent publish(
            Long ownerId, String type, Map<String, Long> refs, Map<String, Object> payload) {
        var event = new SysEvent();
        event.setOwnerId(ownerId);
        event.setOrgId(OrgContext.getCurrentOrgId());
        event.setWorkspaceId(OrgContext.getCurrentWorkspaceId());
        event.setEventType(type);
        event.setRefs(cleanRefs(type, refs));
        event.setPayload(cleanPayload(payload));
        repository.lockOwner(ownerId);
        repository.saveAndFlush(event);
        afterCommit(() -> broadcast(event));
        return event;
    }

    private void broadcast(SysEvent event) {
        try {
            streamService.broadcast(event);
        } catch (RuntimeException error) {
            // 事件已落库，广播失败由心跳补发兜底，不得影响已提交的业务事务
            log.error("事件广播失败: eventId={}, type={}", event.getId(), event.getEventType(), error);
        }
    }

    private Map<String, Long> cleanRefs(String type, Map<String, Long> refs) {
        var result = new LinkedHashMap<String, Long>();
        if (refs == null) {
            return result;
        }
        refs.forEach(
                (key, value) -> {
                    if (value == null) {
                        return;
                    }
                    if (value <= 0) {
                        log.warn("事件关联值非正整数，已丢弃: type={}, key={}, value={}", type, key, value);
                        return;
                    }
                    result.put(key, value);
                });
        return result;
    }

    private Map<String, Object> cleanPayload(Map<String, Object> payload) {
        var result = new LinkedHashMap<String, Object>();
        if (payload != null) {
            payload.forEach(
                    (key, value) -> {
                        if (value != null) {
                            result.put(key, value);
                        }
                    });
        }
        return result;
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
}
