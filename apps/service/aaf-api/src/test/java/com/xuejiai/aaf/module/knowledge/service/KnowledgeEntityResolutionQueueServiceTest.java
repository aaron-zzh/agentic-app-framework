package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionService;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.TaskQueue;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class KnowledgeEntityResolutionQueueServiceTest extends BaseMockitoUnitTest {

    @Mock private TaskQueue taskQueue;
    @Mock private EntityResolutionService entityResolutionService;
    @Mock private PermissionExecutionService permissionExecutionService;
    @InjectMocks private KnowledgeEntityResolutionQueueService queueService;

    @AfterEach
    void clearContext() {
        OrgContext.clear();
    }

    @Test
    @DisplayName("Given 知识库信息 When 入队 Then 使用知识库维度的稳定任务 ID")
    void should_use_stable_task_id_keyed_by_knowledge_base() {
        // 调用
        queueService.enqueue(3L, 7L, 5L, 6L);

        // 断言
        var captor = ArgumentCaptor.forClass(AsyncTaskMessage.class);
        verify(taskQueue).enqueue(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo("knowledge-entity-resolution:3");
        assertThat(captor.getValue().type()).isEqualTo("KNOWLEDGE_ENTITY_RESOLUTION");
    }

    @Test
    @DisplayName("Given 消费任务 When 处理 Then 在归属者权限和租户上下文内触发消歧")
    void should_resolve_entities_in_owner_permission_and_org_context() {
        // 准备参数
        doAnswer(
                        invocation -> {
                            Runnable action = invocation.getArgument(2);
                            action.run();
                            return null;
                        })
                .when(permissionExecutionService)
                .runAsOwner(eq(7L), eq("knowledge-entity-resolution"), any(Runnable.class));

        // 调用
        queueService.handle(
                "knowledge-entity-resolution:3",
                JsonUtils.toJsonString(
                        new KnowledgeEntityResolutionQueueService.ResolveEntityPayload(
                                3L, 7L, 5L, 6L)));

        // 断言
        verify(entityResolutionService).resolve(3L);
        verify(permissionExecutionService)
                .runAsOwner(eq(7L), eq("knowledge-entity-resolution"), any(Runnable.class));
    }
}
