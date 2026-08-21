package com.xuejiai.aaf.module.system.todo.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.TODO_QUEUE_PAYLOAD_INVALID;

import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.task.AsyncQueueTaskService;
import com.xuejiai.aaf.framework.task.queue.TaskHandler;

import lombok.RequiredArgsConstructor;

/** Todo 队列试点：异步执行管理端已完成待办清理。 */
@Service
@RequiredArgsConstructor
public class TodoQueueService implements TaskHandler {

    public static final String TASK_TYPE = "TODO_CLEAR_DONE";

    private final AsyncQueueTaskService asyncTaskService;
    private final TodoService todoService;
    private final OperatorContext operatorContext;
    private final PermissionExecutionService permissionExecutionService;

    /** 提交低优先级清理任务并返回可查询的持久化任务引用。 */
    public AsyncQueueTaskService.AsyncTaskRef enqueueClearDone() {
        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> new AccessDeniedException("请求未认证"));
        var payload =
                new ClearDonePayload(
                        ownerId, OrgContext.getCurrentOrgId(), OrgContext.getCurrentWorkspaceId());
        return asyncTaskService.submit(
                TASK_TYPE,
                JsonUtils.toJsonString(payload),
                8,
                ownerId,
                payload.orgId(),
                payload.workspaceId());
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    /** 清理操作本身幂等，重复执行只会再次得到删除数量 0。 */
    @Override
    public String handle(String taskId, String payloadJson) {
        var payload = JsonUtils.parseObject(payloadJson, ClearDonePayload.class);
        if (payload == null || payload.ownerId() == null || payload.ownerId() <= 0) {
            throw exception(TODO_QUEUE_PAYLOAD_INVALID);
        }
        var deletedCount =
                permissionExecutionService.runAsOwner(
                        payload.ownerId(),
                        "todo-clear-done-queue",
                        () -> runInOrgContext(payload, todoService::clearDoneTodos));
        return JsonUtils.toJsonString(new ClearDoneResult(deletedCount));
    }

    private <T> T runInOrgContext(ClearDonePayload payload, Supplier<T> action) {
        var previousOrgId = OrgContext.getCurrentOrgId();
        var previousWorkspaceId = OrgContext.getCurrentWorkspaceId();
        OrgContext.setCurrentOrgId(payload.orgId());
        OrgContext.setCurrentWorkspaceId(payload.workspaceId());
        try {
            return action.get();
        } finally {
            OrgContext.setCurrentOrgId(previousOrgId);
            OrgContext.setCurrentWorkspaceId(previousWorkspaceId);
        }
    }

    record ClearDonePayload(Long ownerId, Long orgId, Long workspaceId) {}

    record ClearDoneResult(long deletedCount) {}
}
