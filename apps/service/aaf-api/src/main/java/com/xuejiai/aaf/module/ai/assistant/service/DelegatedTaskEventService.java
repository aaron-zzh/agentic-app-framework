package com.xuejiai.aaf.module.ai.assistant.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventReducer;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.event.AafAiTaskSnapshot;
import com.xuejiai.aaf.module.ai.event.HeadlessProjector;

/** 委托任务执行事件 cursor replay、快照与订阅入口。 */
@Service
public class DelegatedTaskEventService {

    private final DelegatedTaskPort tasks;
    private final ExecutionEventStorePort events;
    private final DelegatedTaskEventStreamService streams;
    private final HeadlessProjector projector;
    private final OperatorContext operators;

    public DelegatedTaskEventService(
            DelegatedTaskPort tasks,
            ExecutionEventStorePort events,
            DelegatedTaskEventStreamService streams,
            HeadlessProjector projector,
            OperatorContext operators) {
        this.tasks = tasks;
        this.events = events;
        this.streams = streams;
        this.projector = projector;
        this.operators = operators;
    }

    public AafAiTaskSnapshot snapshot(String taskId, long afterEventOffset) {
        if (afterEventOffset < 0) {
            throw new IllegalArgumentException("afterEventOffset 不能为负数");
        }
        var task = requireOwned(taskId);
        var stored =
                events.readTask(task.tenantId(), task.taskId(), 0)
                        .collectList()
                        .blockOptional()
                        .orElseGet(List::of);
        var state = ExecutionEventReducer.reduceStored(stored);
        var projected =
                stored.stream()
                        .filter(event -> event.eventOffset() > afterEventOffset)
                        .map(projector::project)
                        .toList();
        var nextEventOffset =
                state.eventOffset() == null
                        ? afterEventOffset
                        : Math.max(afterEventOffset, state.eventOffset());
        return new AafAiTaskSnapshot(
                afterEventOffset, nextEventOffset, "AT_LEAST_ONCE", state.publicState(), projected);
    }

    public SseEmitter subscribe(String taskId, long afterEventOffset) {
        var task = requireOwned(taskId);
        return streams.subscribe(task.tenantId(), task.taskId(), afterEventOffset);
    }

    private DelegatedTask requireOwned(String taskId) {
        var task =
                tasks.find(tenantId(), new TaskId(taskId))
                        .orElseThrow(() -> new IllegalArgumentException("委托任务不存在"))
                        .task();
        if (!task.userId().equals(userId())) {
            throw new AccessDeniedException("无权访问该委托任务");
        }
        return task;
    }

    private static TenantId tenantId() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new AccessDeniedException("请求缺少组织上下文");
        }
        return new TenantId(orgId.toString());
    }

    private UserId userId() {
        var value =
                operators.currentOwnerId().orElseThrow(() -> new AccessDeniedException("请求未认证"));
        return new UserId(value.toString());
    }
}
