package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgePipelineService;
import com.xuejiai.aaf.framework.engine.knowledge.pipeline.PipelineResult;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.TaskQueue;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class KnowledgeDocumentQueueServiceTest extends BaseMockitoUnitTest {

    @Mock private TaskQueue taskQueue;
    @Mock private KnowledgeDocumentRepository documentRepository;
    @Mock private StorageService storageService;
    @Mock private KnowledgePipelineService pipelineService;
    @Mock private PermissionExecutionService permissionExecutionService;
    @Mock private KnowledgeDocumentExecutionLeaseService executionLeaseService;
    @InjectMocks private KnowledgeDocumentQueueService queueService;

    @AfterEach
    void clearContext() {
        OrgContext.clear();
    }

    @Test
    @DisplayName("Given 同一知识文档 When 重复派发 Then 始终使用稳定任务 ID")
    void should_use_stable_task_id_for_document_dispatch() {
        // 准备参数
        var document = document(DocumentStatusEnum.PENDING.getCode());
        var taskCaptor = ArgumentCaptor.forClass(AsyncTaskMessage.class);

        // 调用
        var taskId = queueService.enqueue(document);

        // 断言
        verify(taskQueue).enqueue(taskCaptor.capture());
        assertThat(taskId).isEqualTo("knowledge-document:11");
        assertThat(taskCaptor.getValue().id()).isEqualTo(taskId);
    }

    @Test
    @DisplayName("Given 文档已完成 When 重复消费任务 Then 不重复下载和向量化")
    void should_skip_when_document_already_completed() {
        // 准备参数
        var document = document(DocumentStatusEnum.COMPLETED.getCode());
        when(documentRepository.findById(11L)).thenReturn(Optional.of(document));
        runPermissionCallback();

        // 调用
        queueService.handle(payload());

        // 断言
        verify(storageService, never()).download(any());
        verify(pipelineService, never()).process(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Given 排队文档已删除 When 消费陈旧消息 Then 幂等结束且不触发管道")
    void should_no_op_when_stale_message_document_is_missing() {
        // 准备参数
        when(documentRepository.findById(11L)).thenReturn(Optional.empty());
        runPermissionCallback();

        // 调用
        queueService.handle(payload());

        // 断言
        verify(storageService, never()).download(any());
        verify(pipelineService, never()).process(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Given 知识管道返回失败 When 消费任务 Then 抛出异常触发统一重试")
    void should_throw_when_pipeline_returns_failure() {
        // 准备参数
        var document = document(DocumentStatusEnum.PENDING.getCode());
        when(documentRepository.findById(11L)).thenReturn(Optional.of(document));
        when(storageService.download("kb/guide.md"))
                .thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(pipelineService.process(eq(3L), eq(11L), any(), eq("guide.md"), any(Runnable.class)))
                .thenReturn(new PipelineResult(false, 11L, 0, 0, 10, "embedding unavailable"));
        runPermissionCallback();

        // 调用 + 断言
        assertThatThrownBy(() -> queueService.handle(payload()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding unavailable");
        verify(permissionExecutionService)
                .runAsOwner(eq(7L), eq("knowledge-document-process"), any(Runnable.class));
    }

    @Test
    @DisplayName("Given 待处理文档 When 消费成功 Then 在任务租户上下文执行并恢复原上下文")
    void should_process_in_task_context_and_restore_previous_context() {
        // 准备参数
        var document = document(DocumentStatusEnum.PENDING.getCode());
        when(documentRepository.findById(11L)).thenReturn(Optional.of(document));
        when(storageService.download("kb/guide.md"))
                .thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(pipelineService.process(eq(3L), eq(11L), any(), eq("guide.md"), any(Runnable.class)))
                .thenAnswer(
                        invocation -> {
                            assertThat(OrgContext.getCurrentOrgId()).isEqualTo(5L);
                            assertThat(OrgContext.getCurrentWorkspaceId()).isEqualTo(6L);
                            return new PipelineResult(true, 11L, 1, 1, 10, null);
                        });
        runPermissionCallback();
        OrgContext.setCurrentOrgId(50L);
        OrgContext.setCurrentWorkspaceId(60L);

        // 调用
        queueService.handle(payload());

        // 断言
        verify(pipelineService)
                .process(eq(3L), eq(11L), any(), eq("guide.md"), any(Runnable.class));
        assertThat(OrgContext.getCurrentOrgId()).isEqualTo(50L);
        assertThat(OrgContext.getCurrentWorkspaceId()).isEqualTo(60L);
    }

    @Test
    @DisplayName("Given 任务租户与文档不一致 When 消费任务 Then 拒绝处理")
    void should_reject_when_document_scope_mismatches_payload() {
        // 准备参数
        var document = document(DocumentStatusEnum.PENDING.getCode());
        document.setOrgId(99L);
        when(documentRepository.findById(11L)).thenReturn(Optional.of(document));
        runPermissionCallback();

        // 调用 + 断言
        assertThatThrownBy(() -> queueService.handle(payload()))
                .isInstanceOf(RuntimeException.class);
        verify(storageService, never()).download(any());
        verify(pipelineService, never()).process(any(), any(), any(), any(), any());
    }

    private void runPermissionCallback() {
        doAnswer(
                        invocation -> {
                            KnowledgeDocumentExecutionLeaseService.GuardedAction action =
                                    invocation.getArgument(1);
                            action.run(() -> {});
                            return null;
                        })
                .when(executionLeaseService)
                .execute(eq(11L), any(KnowledgeDocumentExecutionLeaseService.GuardedAction.class));
        doAnswer(
                        invocation -> {
                            Runnable callback = invocation.getArgument(2);
                            callback.run();
                            return null;
                        })
                .when(permissionExecutionService)
                .runAsOwner(eq(7L), eq("knowledge-document-process"), any(Runnable.class));
    }

    private String payload() {
        return JsonUtils.toJsonString(
                new KnowledgeDocumentQueueService.ProcessDocumentPayload(11L, 7L, 5L, 6L));
    }

    private KnowledgeDocument document(Integer status) {
        var document = new KnowledgeDocument();
        document.setId(11L);
        document.setKnowledgeBaseId(3L);
        document.setOwnerId(7L);
        document.setOrgId(5L);
        document.setWorkspaceId(6L);
        document.setTitle("guide.md");
        document.setFilePath("kb/guide.md");
        document.setStatus(status);
        return document;
    }
}
