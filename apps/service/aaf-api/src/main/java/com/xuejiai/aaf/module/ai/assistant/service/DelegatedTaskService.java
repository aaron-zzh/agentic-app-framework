package com.xuejiai.aaf.module.ai.assistant.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Source;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
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
    private static final AssistantId DEFAULT_ASSISTANT_ID =
            new AssistantId("system.assistant.default-user");
    private static final AssistantVersion DEFAULT_ASSISTANT_VERSION = new AssistantVersion(1);

    private final DelegatedTaskPort tasks;
    private final TaskBoardPort boards;
    private final DelegatedTaskCoordinator coordinator;
    private final OperatorContext operators;

    public DelegatedTaskService(
            DelegatedTaskPort tasks,
            TaskBoardPort boards,
            DelegatedTaskCoordinator coordinator,
            OperatorContext operators) {
        this.tasks = tasks;
        this.boards = boards;
        this.coordinator = coordinator;
        this.operators = operators;
    }

    public DelegatedTaskVO create(
            Source source, String conversationId, String title, String description, int priority) {
        var command = createCommand(conversationId, title, description);
        var task =
                switch (source) {
                    case CONVERSATION ->
                            coordinator.submitConversationTask(
                                    command, title, description, priority);
                    case MANUAL ->
                            coordinator.submitManualTask(
                                    command, title, description, priority, null);
                    case AUTOMATION ->
                            throw new IllegalArgumentException("API 不允许创建 AUTOMATION 来源任务");
                };
        return toVO(task);
    }

    public List<DelegatedTaskVO> list(String status) {
        var expected =
                status == null || status.isBlank() ? null : Status.valueOf(status.toUpperCase());
        return tasks.list(tenantId(), userId()).stream()
                .filter(task -> expected == null || task.status() == expected)
                .map(this::toVO)
                .toList();
    }

    public DelegatedTaskVO get(String taskId) {
        var task =
                tasks.find(tenantId(), new TaskId(taskId))
                        .orElseThrow(() -> new IllegalArgumentException("委托任务不存在"))
                        .task();
        if (!task.userId().equals(userId())) throw new AccessDeniedException("无权访问该委托任务");
        return toVO(task);
    }

    public DelegatedTaskVO stop(String taskId, String reason) {
        return toVO(coordinator.stop(tenantId(), userId(), new TaskId(taskId), reason));
    }

    public DelegatedTaskVO takeOver(String taskId, String reason) {
        return toVO(coordinator.takeOver(tenantId(), userId(), new TaskId(taskId), reason));
    }

    public DelegatedTaskVO handBack(String taskId) {
        return toVO(coordinator.handBack(tenantId(), userId(), new TaskId(taskId)));
    }

    public Mono<DelegatedTaskVO> acceptInput(String taskId, DelegatedTaskInputDTO input) {
        var command =
                new ExecutionInput(
                        input.inputId(),
                        tenantId(),
                        userId(),
                        new TaskId(taskId),
                        input.kind(),
                        input.content(),
                        Instant.now());
        return coordinator.acceptInput(command).map(this::toVO);
    }

    private DelegatedTaskVO toVO(DelegatedTask task) {
        var goalDescription =
                boards.find(task.tenantId(), task.taskId())
                        .map(board -> board.goal().description())
                        .orElse(null);
        return DelegatedTaskVO.from(task, goalDescription);
    }

    private AssistantCommand createCommand(
            String conversationId, String title, String description) {
        var tenantId = tenantId();
        var userId = userId();
        var seed = UUID.randomUUID().toString();
        var taskId = new TaskId("delegated-task:" + seed);
        var executionId = new ExecutionId("delegated-execution:" + seed);
        var input = description == null || description.isBlank() ? title : description;
        var now = Instant.now();
        return new AssistantCommand(
                AssistantCommand.Operation.START,
                tenantId,
                userId,
                new MemorySubject(tenantId, SubjectKind.USER, userId.value()),
                DEFAULT_ASSISTANT_ID,
                DEFAULT_ASSISTANT_VERSION,
                new ConversationId(conversationId),
                new SessionId("delegated-session:" + seed),
                taskId,
                executionId,
                new RunId("delegated-run:" + seed),
                null,
                new CorrelationId("delegated-correlation:" + seed),
                null,
                new IdempotencyKey("delegated-idempotency:" + seed),
                ControlMode.READ_ONLY,
                null,
                null,
                0,
                input,
                CompletionCriteria.responseDelivered(),
                List.of(),
                TaskModelSelection.auto(),
                now);
    }

    private TenantId tenantId() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) throw new AccessDeniedException("请求缺少组织上下文");
        return new TenantId(orgId.toString());
    }

    private UserId userId() {
        var value =
                operators.currentOwnerId().orElseThrow(() -> new AccessDeniedException("请求未认证"));
        return new UserId(value.toString());
    }
}
