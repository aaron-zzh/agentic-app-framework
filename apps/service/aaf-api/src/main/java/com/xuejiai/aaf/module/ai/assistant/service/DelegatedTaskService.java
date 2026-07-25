package com.xuejiai.aaf.module.ai.assistant.service;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.assistant.vo.DelegatedTaskInputDTO;
import com.xuejiai.aaf.module.ai.assistant.vo.DelegatedTaskVO;
import reactor.core.publisher.Mono;

@Service
public class DelegatedTaskService {
    private final DelegatedTaskPort tasks;
    private final DelegatedTaskCoordinator coordinator;
    private final OperatorContext operators;

    public DelegatedTaskService(
            DelegatedTaskPort tasks,
            DelegatedTaskCoordinator coordinator,
            OperatorContext operators) {
        this.tasks = tasks;
        this.coordinator = coordinator;
        this.operators = operators;
    }

    public List<DelegatedTaskVO> list(String status) {
        var expected = status == null || status.isBlank() ? null : Status.valueOf(status.toUpperCase());
        return tasks.list(tenantId(), userId()).stream()
                .filter(task -> expected == null || task.status() == expected)
                .map(DelegatedTaskVO::from)
                .toList();
    }

    public DelegatedTaskVO get(String taskId) {
        var task = tasks.find(tenantId(), new TaskId(taskId))
                .orElseThrow(() -> new IllegalArgumentException("委托任务不存在"))
                .task();
        if (!task.userId().equals(userId())) throw new AccessDeniedException("无权访问该委托任务");
        return DelegatedTaskVO.from(task);
    }

    public DelegatedTaskVO stop(String taskId, String reason) {
        return DelegatedTaskVO.from(
                coordinator.stop(tenantId(), userId(), new TaskId(taskId), reason));
    }

    public DelegatedTaskVO takeOver(String taskId, String reason) {
        return DelegatedTaskVO.from(
                coordinator.takeOver(tenantId(), userId(), new TaskId(taskId), reason));
    }

    public DelegatedTaskVO handBack(String taskId) {
        return DelegatedTaskVO.from(
                coordinator.handBack(tenantId(), userId(), new TaskId(taskId)));
    }

    public Mono<DelegatedTaskVO> acceptInput(String taskId, DelegatedTaskInputDTO input) {
        var command = new ExecutionInput(
                input.inputId(), tenantId(), userId(), new TaskId(taskId),
                input.kind(), input.content(), Instant.now());
        return coordinator.acceptInput(command).map(DelegatedTaskVO::from);
    }

    private TenantId tenantId() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) throw new AccessDeniedException("请求缺少组织上下文");
        return new TenantId(orgId.toString());
    }

    private UserId userId() {
        var value = operators.currentOwnerId()
                .orElseThrow(() -> new AccessDeniedException("请求未认证"));
        return new UserId(value.toString());
    }
}
