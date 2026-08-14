package com.xuejiai.aaf.module.system.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionContextHolder;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.task.AsyncQueueTaskService;
import com.xuejiai.aaf.framework.task.AsyncTaskStatus;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class TodoQueueServiceTest extends BaseMockitoUnitTest {

    @Mock private AsyncQueueTaskService asyncTaskService;
    @Mock private TodoService todoService;
    @Mock private OperatorContext operatorContext;

    private TodoQueueService queueService;

    @BeforeEach
    void setUp() {
        queueService =
                new TodoQueueService(
                        asyncTaskService,
                        todoService,
                        operatorContext,
                        new PermissionExecutionService());
    }

    @AfterEach
    void tearDown() {
        OrgContext.clear();
        PermissionExecutionContextHolder.clear();
    }

    @Test
    @DisplayName("Given 管理员与组织上下文 When 提交异步清理 Then 创建低优先级持久化任务")
    void should_enqueue_clear_done_with_owner_and_org_context() {
        // 准备参数
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        OrgContext.setCurrentOrgId(11L);
        OrgContext.setCurrentWorkspaceId(13L);
        var expected =
                new AsyncQueueTaskService.AsyncTaskRef(
                        "todo-task-1", TodoQueueService.TASK_TYPE, AsyncTaskStatus.PENDING);
        when(asyncTaskService.submit(
                        org.mockito.ArgumentMatchers.eq(TodoQueueService.TASK_TYPE),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(8),
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(11L),
                        org.mockito.ArgumentMatchers.eq(13L)))
                .thenReturn(expected);

        // 调用
        var task = queueService.enqueueClearDone();

        // 断言
        assertThat(task).isEqualTo(expected);
        verify(asyncTaskService)
                .submit(
                        org.mockito.ArgumentMatchers.eq(TodoQueueService.TASK_TYPE),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(8),
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(11L),
                        org.mockito.ArgumentMatchers.eq(13L));
    }

    @Test
    @DisplayName("Given 队列携带任务上下文 When 消费清理 Then 恢复权限组织并在结束后还原")
    void should_restore_and_clear_context_when_handling() {
        // 准备参数
        OrgContext.setCurrentOrgId(1L);
        OrgContext.setCurrentWorkspaceId(2L);
        var payload = JsonUtils.toJsonString(new TodoQueueService.ClearDonePayload(7L, 11L, 13L));
        doAnswer(
                        invocation -> {
                            assertThat(PermissionExecutionContextHolder.get().ownerId())
                                    .isEqualTo(7L);
                            assertThat(OrgContext.getCurrentOrgId()).isEqualTo(11L);
                            assertThat(OrgContext.getCurrentWorkspaceId()).isEqualTo(13L);
                            return 3L;
                        })
                .when(todoService)
                .clearDoneTodos();

        // 调用
        var result = queueService.handle("todo-task-1", payload);

        // 断言
        assertThat(
                        JsonUtils.parseObject(result, TodoQueueService.ClearDoneResult.class)
                                .deletedCount())
                .isEqualTo(3L);
        verify(todoService).clearDoneTodos();
        assertThat(PermissionExecutionContextHolder.get()).isNull();
        assertThat(OrgContext.getCurrentOrgId()).isEqualTo(1L);
        assertThat(OrgContext.getCurrentWorkspaceId()).isEqualTo(2L);
    }
}
