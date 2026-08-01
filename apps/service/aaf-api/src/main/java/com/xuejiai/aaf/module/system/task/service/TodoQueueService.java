package com.xuejiai.aaf.module.system.task.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.TaskHandler;
import com.xuejiai.aaf.framework.task.queue.TaskQueue;

import lombok.RequiredArgsConstructor;

/** Todo 队列试点：异步执行管理端已完成待办清理。 */
@Service
@RequiredArgsConstructor
public class TodoQueueService implements TaskHandler {

    public static final String TASK_TYPE = "TODO_CLEAR_DONE";

    private final TaskQueue taskQueue;
    private final TodoService todoService;
    private final OperatorContext operatorContext;
    private final PermissionExecutionService permissionExecutionService;

    /** 提交低优先级清理任务，返回稳定业务任务 ID。 */
    public String enqueueClearDone() {
        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
        var payload =
                new ClearDonePayload(
                        ownerId, OrgContext.getCurrentOrgId(), OrgContext.getCurrentWorkspaceId());
        var task = new AsyncTaskMessage(TASK_TYPE, JsonUtils.toJsonString(payload), 8);
        taskQueue.enqueue(task);
        return task.id();
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    /** 清理操作本身幂等，重复执行只会再次得到删除数量 0。 */
    @Override
    public void handle(String taskId, String payloadJson) {
        var payload = JsonUtils.parseObject(payloadJson, ClearDonePayload.class);
        if (payload == null || payload.ownerId() == null || payload.ownerId() <= 0) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        permissionExecutionService.runAsOwner(
                payload.ownerId(),
                "todo-clear-done-queue",
                () -> runInOrgContext(payload, todoService::clearDoneTodos));
    }

    private void runInOrgContext(ClearDonePayload payload, Runnable action) {
        var previousOrgId = OrgContext.getCurrentOrgId();
        var previousWorkspaceId = OrgContext.getCurrentWorkspaceId();
        OrgContext.setCurrentOrgId(payload.orgId());
        OrgContext.setCurrentWorkspaceId(payload.workspaceId());
        try {
            action.run();
        } finally {
            OrgContext.setCurrentOrgId(previousOrgId);
            OrgContext.setCurrentWorkspaceId(previousWorkspaceId);
        }
    }

    record ClearDonePayload(Long ownerId, Long orgId, Long workspaceId) {}
}
