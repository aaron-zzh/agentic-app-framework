package com.xuejiai.aaf.module.system.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionContextHolder;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.TaskQueue;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class TodoQueueServiceTest extends BaseMockitoUnitTest {

    @Mock private TaskQueue taskQueue;
    @Mock private TodoService todoService;
    @Mock private OperatorContext operatorContext;

    private TodoQueueService queueService;

    @BeforeEach
    void setUp() {
        queueService =
                new TodoQueueService(
                        taskQueue, todoService, operatorContext, new PermissionExecutionService());
    }

    @AfterEach
    void tearDown() {
        OrgContext.clear();
        PermissionExecutionContextHolder.clear();
    }

    @Test
    @DisplayName("Given 管理员与组织上下文 When 提交异步清理 Then 入队低优先级稳定任务")
    void should_enqueue_clear_done_with_owner_and_org_context() {
        // 准备参数
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        OrgContext.setCurrentOrgId(11L);
        OrgContext.setCurrentWorkspaceId(13L);
        var captor = ArgumentCaptor.forClass(AsyncTaskMessage.class);

        // 调用
        var taskId = queueService.enqueueClearDone();

        // 断言
        verify(taskQueue).enqueue(captor.capture());
        var task = captor.getValue();
        var payload =
                JsonUtils.parseObject(task.payload(), TodoQueueService.ClearDonePayload.class);
        assertThat(taskId).isEqualTo(task.id());
        assertThat(task.type()).isEqualTo(TodoQueueService.TASK_TYPE);
        assertThat(task.priority()).isEqualTo(8);
        assertThat(payload.ownerId()).isEqualTo(7L);
        assertThat(payload.orgId()).isEqualTo(11L);
        assertThat(payload.workspaceId()).isEqualTo(13L);
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
        queueService.handle(payload);

        // 断言
        verify(todoService).clearDoneTodos();
        assertThat(PermissionExecutionContextHolder.get()).isNull();
        assertThat(OrgContext.getCurrentOrgId()).isEqualTo(1L);
        assertThat(OrgContext.getCurrentWorkspaceId()).isEqualTo(2L);
    }
}
