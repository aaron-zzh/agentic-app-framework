package com.xuejiai.aaf.module.ai.aigc.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcBoundTaskEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskSubmitCommand;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcTaskApiAdapterTest extends BaseMockitoUnitTest {

    @Mock private AigcTaskService taskService;
    @Mock private AigcTaskRepository taskRepository;
    @Mock private OperatorContext operatorContext;
    @Mock private AigcBoundTaskEvidencePort boundTaskEvidencePort;
    @Mock private AigcTaskIntentStore intentStore;
    @Mock private AigcTaskProviderCapabilities providerCapabilities;
    @Mock private CapabilityRouter capabilityRouter;
    @Mock private AiCreditGuard creditGuard;
    @Mock private AigcSubmissionAccessGuard submissionAccessGuard;
    @Mock private AigcTaskExecutor taskExecutor;
    @Mock private AigcActivityEventService activityEventService;
    @InjectMocks private AigcTaskApiAdapter adapter;

    @Test
    @DisplayName("Given BOUND execution When 提交项目 Task Then 完整 PREPARED intent 提交后才 dispatch")
    void should_persist_complete_intent_before_dispatch_when_execution_is_bound() {
        // 准备参数
        var command =
                new AigcTaskSubmitCommand(
                        31L,
                        21L,
                        22L,
                        "IMAGE",
                        "model:image",
                        "画一只猫",
                        "{\"width\":1024}",
                        "idem-1");
        var evidence = new AigcBoundTaskEvidencePort.BoundEvidence(9L, 8L, 7L, 41L, 42L, 31L);
        var model = model();
        when(boundTaskEvidencePort.requireBound(31L, 21L, 22L)).thenReturn(evidence);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(9L));
        when(capabilityRouter.resolve(any(CapabilityRoutingContext.class))).thenReturn(model);
        when(taskService.estimateCredits(eq(9L), eq("IMAGE"), eq("model:image"), anyMap()))
                .thenReturn(7L);
        when(intentStore.prepare(any(AigcTask.class)))
                .thenAnswer(
                        invocation -> {
                            var task = invocation.<AigcTask>getArgument(0);
                            task.setId(51L);
                            return new AigcTaskIntentStore.PrepareResult(task, true);
                        });
        TransactionSynchronizationManager.initSynchronization();

        try {
            // 调用
            var view = adapter.submit(command);

            // 断言
            var captor = ArgumentCaptor.forClass(AigcTask.class);
            verify(intentStore).prepare(captor.capture());
            var intent = captor.getValue();
            assertThat(intent.getExecutionRunId()).isEqualTo(31L);
            assertThat(intent.getProjectId()).isEqualTo(21L);
            assertThat(intent.getProjectObjectId()).isEqualTo(22L);
            assertThat(intent.getIdempotencyKey()).isEqualTo("idem-1");
            assertThat(intent.getRequestHash()).hasSize(64);
            assertThat(intent.getProviderKey()).startsWith("task-");
            assertThat(intent.getParams())
                    .contains(
                            "providerIdempotencyKey",
                            "providerIdempotentSubmission",
                            "providerReceiptLookup");
            assertThat(intent.getStatus()).isEqualTo("PREPARED");
            assertThat(view.id()).isEqualTo(51L);
            verify(submissionAccessGuard)
                    .requireAccess(9L, com.xuejiai.aaf.common.enums.aigc.AigcTaskTypeEnum.IMAGE);
            verify(taskExecutor, never()).resumeIntent(51L);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
            verify(taskExecutor).resumeIntent(51L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Given durable intent 已重放 When 再次提交 Then 返回原 Task 且不重复 dispatch")
    void should_not_dispatch_when_intent_is_replayed() {
        // 准备参数
        var command =
                new AigcTaskSubmitCommand(
                        31L, 21L, null, "IMAGE", "model:image", "画一只猫", "{}", "idem-1");
        when(boundTaskEvidencePort.requireBound(31L, 21L, null))
                .thenReturn(new AigcBoundTaskEvidencePort.BoundEvidence(9L, 8L, 7L, 41L, 42L, 31L));
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(9L));
        var model = model();
        when(capabilityRouter.resolve(any(CapabilityRoutingContext.class))).thenReturn(model);
        when(taskService.estimateCredits(eq(9L), eq("IMAGE"), eq("model:image"), anyMap()))
                .thenReturn(7L);
        var existing = new AigcTask();
        existing.setId(51L);
        existing.setExecutionRunId(31L);
        existing.setType("IMAGE");
        existing.setStatus("PREPARED");
        when(intentStore.prepare(any(AigcTask.class)))
                .thenReturn(new AigcTaskIntentStore.PrepareResult(existing, false));

        // 调用
        var view = adapter.submit(command);

        // 断言
        assertThat(view.id()).isEqualTo(51L);
        verify(taskExecutor, never()).resumeIntent(any());
    }

    @Test
    @DisplayName("Given 无 execution 的公共 Task 携带 project When 提交 Then 拒绝项目绑定")
    void should_reject_project_binding_when_execution_is_absent() {
        // 准备参数
        var command =
                new AigcTaskSubmitCommand(
                        null, 21L, null, "IMAGE", "model:image", "画一只猫", "{}", null);

        // 调用 + 断言
        assertThatThrownBy(() -> adapter.submit(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("独立 Task 不允许绑定 project");
        verify(boundTaskEvidencePort, never()).requireBound(any(), any(), any());
        verify(intentStore, never()).prepare(any());
    }

    private AiModel model() {
        when(providerCapabilities.require(AiModelProviderType.DASHSCOPE))
                .thenReturn(new AigcTaskProviderCapabilities.Capabilities(false, false));
        var model = new AiModel();
        model.setModelId("model:image");
        model.setDisplayName("Image Model");
        model.setProviderType(AiModelProviderType.DASHSCOPE);
        return model;
    }
}
