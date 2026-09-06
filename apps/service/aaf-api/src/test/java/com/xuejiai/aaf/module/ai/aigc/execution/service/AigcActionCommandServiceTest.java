package com.xuejiai.aaf.module.ai.aigc.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcChildActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeCancellationPort;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionSubmission;
import com.xuejiai.aaf.module.ai.aigc.execution.event.AigcExecutionRunDispatchRequestedEvent;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionSubmissionRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionTaskRefRepository;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationReleaseCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectGraphView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskApi;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcActionCommandServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcProjectApi projectApi;
    @Mock private AigcExecutionBindingResolver bindingResolver;
    @Mock private AigcExecutionRunRepository runRepository;
    @Mock private AigcExecutionSubmissionRepository submissionRepository;
    @Mock private AigcExecutionSubmissionService submissionService;
    @Mock private AigcExecutionTerminalService terminalService;
    @Mock private AigcExecutionTaskRefRepository taskRefRepository;
    @Mock private AigcTaskApi taskApi;
    @Mock private AigcMediaApi mediaApi;
    @Mock private AigcRuntimeCancellationPort runtimeCancellationPort;
    @Mock private OperatorContext operatorContext;
    @Mock private AigcActionExecutor executor;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private AigcActionCommandService service;

    @BeforeEach
    void setUp() {
        service =
                new AigcActionCommandService(
                        projectApi,
                        bindingResolver,
                        runRepository,
                        submissionRepository,
                        submissionService,
                        terminalService,
                        taskRefRepository,
                        taskApi,
                        mediaApi,
                        runtimeCancellationPort,
                        operatorContext,
                        List.of(executor),
                        eventPublisher);
        lenient().when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        lenient().when(projectApi.requireProject(1L)).thenReturn(project());
        lenient()
                .when(
                        runRepository
                                .findByProjectIdAndActionKeyAndDeletedFalseOrderByIdDesc(
                                        any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("Given 图片对象和视频动作 When 显式提交 objectId Then 拒绝不适用对象类型")
    void should_reject_object_when_action_does_not_support_object_type() {
        // 准备参数
        var object =
                new AigcProjectObjectView(
                        11L,
                        1L,
                        "image.01",
                        "image",
                        1,
                        "image_deliverable",
                        "图片",
                        "REQUIRED",
                        "image.generate",
                        null,
                        "draft",
                        null);
        when(projectApi.getGraph(1L))
                .thenReturn(new AigcProjectGraphView(project(), List.of(object), List.of(), 1L));
        var command =
                new AigcActionCommand(
                        1L,
                        11L,
                        "video.generate",
                        "生成视频",
                        "video-model",
                        Map.of(),
                        List.of(),
                        List.of(11L),
                        1L,
                        true,
                        "idem-video");

        var intent = submission(80L);
        when(submissionService.record(command))
                .thenReturn(new AigcExecutionSubmissionService.SubmissionReceipt(intent, true));
        when(submissionRepository.findLockedById(80L)).thenReturn(Optional.of(intent));
        when(submissionRepository.claimPreparing(80L)).thenReturn(1);

        // 调用 + 断言
        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不适用于对象类型 image_deliverable");
        verify(bindingResolver, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("Given 失败执行含冻结参数 When 重试 Then 通过持久提交恢复参数并发布派发事件")
    void should_restore_frozen_parameters_through_durable_submission_when_retrying() {
        // 准备参数
        var previous = failedRun();
        var object =
                new AigcProjectObjectView(
                        12L,
                        1L,
                        "video.01",
                        "video",
                        1,
                        "video_deliverable",
                        "视频",
                        "REQUIRED",
                        "video.generate",
                        null,
                        "draft",
                        null);
        var binding = new AigcExecutionBinding();
        binding.setId(91L);
        binding.setTargetType("tool");
        binding.setTargetRef("aigc.video.generate");
        binding.setConfirmationRequired(false);
        var intent = submission(80L);
        var savedRun = new java.util.concurrent.atomic.AtomicReference<AigcExecutionRun>();
        when(runRepository.findById(50L)).thenReturn(Optional.of(previous));
        when(submissionService.record(any(AigcActionCommand.class)))
                .thenReturn(new AigcExecutionSubmissionService.SubmissionReceipt(intent, true));
        when(submissionRepository.findLockedById(80L)).thenReturn(Optional.of(intent));
        when(submissionRepository.claimPreparing(80L)).thenReturn(1);
        when(projectApi.getGraph(1L))
                .thenReturn(new AigcProjectGraphView(project(), List.of(object), List.of(), 1L));
        when(bindingResolver.resolve(project(), "video.generate")).thenReturn(binding);
        when(projectApi.reserveExecution(any()))
                .thenReturn(
                        new AigcProjectExecutionReservationView(
                                70L, 80L, 1L, 12L, 1L, List.of(12L), "PREPARED", null));
        when(runRepository.save(any(AigcExecutionRun.class)))
                .thenAnswer(
                        invocation -> {
                            var run = invocation.getArgument(0, AigcExecutionRun.class);
                            if (run.getId() == null) {
                                run.setId(51L);
                            }
                            savedRun.set(run);
                            return run;
                        });
        when(runRepository.findLockedById(51L))
                .thenAnswer(invocation -> Optional.of(savedRun.get()));

        // 调用
        service.retry(50L, "idem-retry");

        // 断言
        var eventCaptor = ArgumentCaptor.forClass(AigcExecutionRunDispatchRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        var command = eventCaptor.getValue().command();
        assertThat(command.requestedModelId()).isEqualTo("video-model-v3");
        assertThat(command.actionArguments())
                .containsExactlyInAnyOrderEntriesOf(Map.of("resolution", "1080p", "duration", 8));
        assertThat(command.attachmentMediaVersionIds()).containsExactly(301L, 302L);
        assertThat(savedRun.get().getSelectedModelVersion()).isEqualTo("video-model-v3");
        assertThat(savedRun.get().getEffectiveInput())
                .containsEntry("requestedModelId", "video-model-v3")
                .containsEntry("actionArguments", Map.of("resolution", "1080p", "duration", 8));
        assertThat(savedRun.get().getRetryOfExecutionRunId()).isEqualTo(50L);
        assertThat(savedRun.get().getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Given submission 未 BOUND When 提交 child Then 拒绝创建子 Run")
    void should_reject_child_when_submission_not_bound() {
        // 准备参数
        var root = orchestrationRun(60L, null, AigcExecutionRunStatus.RUNNING);
        var parent = orchestrationRun(61L, 60L, AigcExecutionRunStatus.RUNNING);
        parent.setRootExecutionRunId(60L);
        var submission = submission(80L);
        submission.setReservationId(70L);
        submission.setRootExecutionRunId(60L);
        submission.setStatus("ROOT_CREATED");
        when(runRepository.findById(61L)).thenReturn(Optional.of(parent));
        when(runRepository.findLockedById(61L)).thenReturn(Optional.of(parent));
        when(runRepository.findLockedById(60L)).thenReturn(Optional.of(root));
        when(submissionRepository.findLockedById(80L)).thenReturn(Optional.of(submission));

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                service.submitChild(
                                        new AigcChildActionCommand(
                                                1L,
                                                12L,
                                                "video.generate",
                                                61L,
                                                60L,
                                                "video-node",
                                                "生成视频",
                                                List.of(),
                                                "child-idem")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("submission 未绑定");
        verify(runRepository, never()).saveAndFlush(any(AigcExecutionRun.class));
    }

    @Test
    @DisplayName("Given 活动 ORCHESTRATION 树 When 取消 root Then 传播取消所有 descendants")
    void should_cancel_whole_tree_when_canceling_orchestration_root() {
        // 准备参数
        var root = orchestrationRun(60L, null, AigcExecutionRunStatus.RUNNING);
        var child = orchestrationRun(61L, 60L, AigcExecutionRunStatus.PENDING);
        child.setRunKind("ACTIVITY");
        when(runRepository.findById(60L)).thenReturn(Optional.of(root));
        when(runRepository.findByRootExecutionRunIdOrderByIdAsc(60L))
                .thenReturn(List.of(root, child));

        // 调用
        service.cancel(60L, "用户取消");

        // 断言
        assertThat(root.getStatus()).isEqualTo(AigcExecutionRunStatus.CANCELED);
        assertThat(child.getStatus()).isEqualTo(AigcExecutionRunStatus.CANCELED);
        verify(terminalService).onRunTerminal(root);
    }

    @Test
    @DisplayName("Given recovery root 尚未绑定 When 恢复失败 Then 释放 PREPARED reservation 并终态化 submission")
    void should_release_prepared_reservation_when_recovery_fails_before_bind() {
        // 准备参数
        var submission = submission(80L);
        submission.setReservationId(70L);
        submission.setRootExecutionRunId(60L);
        submission.setStatus("ROOT_CREATED");
        var root = orchestrationRun(60L, null, AigcExecutionRunStatus.PENDING_BIND);
        when(submissionRepository.findLockedById(80L)).thenReturn(Optional.of(submission));
        when(runRepository.findByRootExecutionRunIdOrderByIdAsc(60L)).thenReturn(List.of(root));
        when(runRepository.findLockedById(60L)).thenReturn(Optional.of(root));

        // 调用
        service.failRecovery(80L, "恢复失败");

        // 断言
        var releaseCaptor =
                ArgumentCaptor.forClass(AigcProjectExecutionReservationReleaseCommand.class);
        verify(projectApi).releaseExecution(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().releaseReason()).isEqualTo("PRE_BIND_CANCEL");
        assertThat(releaseCaptor.getValue().rootExecutionRunId()).isNull();
        assertThat(root.getStatus()).isEqualTo(AigcExecutionRunStatus.FAILED);
        assertThat(submission.getStatus()).isEqualTo("RECOVERY_FAILED");
        verify(terminalService, never()).onRunTerminal(any());
    }

    @Test
    @DisplayName("Given submission 已被另一恢复 owner 认领 When 当前 owner CAS 失败 Then fencing 阻断后续准备")
    void should_stop_preparation_when_submission_fencing_cas_loses() {
        // 准备参数
        var command =
                new AigcActionCommand(
                        1L,
                        null,
                        "brief.refine",
                        "完善简报",
                        null,
                        Map.of(),
                        List.of(),
                        List.of(),
                        1L,
                        true,
                        "idem-fence");
        var intent = submission(80L);
        when(submissionService.record(command))
                .thenReturn(new AigcExecutionSubmissionService.SubmissionReceipt(intent, true));
        when(submissionRepository.findLockedById(80L)).thenReturn(Optional.of(intent));
        when(submissionRepository.claimPreparing(80L)).thenReturn(0);

        // 调用 + 断言
        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他 owner 认领");
        verify(projectApi, never()).reserveExecution(any());
    }

    private AigcExecutionRun orchestrationRun(
            Long id, Long parentId, AigcExecutionRunStatus status) {
        var run = new AigcExecutionRun();
        run.setId(id);
        run.setProjectId(1L);
        run.setObjectId(12L);
        run.setParentExecutionRunId(parentId);
        run.setRootExecutionRunId(parentId == null ? id : 60L);
        run.setRunKind("ORCHESTRATION");
        run.setExecutionSubmissionId(80L);
        run.setExecutionReservationId(70L);
        run.setFrozenProjectObjectIds(List.of(12L));
        run.setStatus(status);
        run.setVersion(1);
        return run;
    }

    private AigcExecutionSubmission submission(Long id) {
        var submission = new AigcExecutionSubmission();
        submission.setId(id);
        submission.setProjectId(1L);
        submission.setIdempotencyKey("intent-" + id);
        submission.setRequestHash("hash-" + id);
        submission.setRequestPayload(Map.of());
        submission.setStatus("INTENT_RECORDED");
        return submission;
    }

    private AigcExecutionRun failedRun() {
        var run = new AigcExecutionRun();
        run.setId(50L);
        run.setProjectId(1L);
        run.setObjectId(12L);
        run.setActionKey("video.generate");
        run.setPromptText("生成视频");
        run.setAttachmentRefs(List.of(301L, 302L));
        run.setEffectiveInput(
                Map.of(
                        "requestedModelId",
                        "video-model-v3",
                        "actionArguments",
                        Map.of("resolution", "1080p", "duration", 8)));
        run.setStatus(AigcExecutionRunStatus.FAILED);
        run.setRetryCount(0);
        run.setFrozenProjectObjectIds(List.of(12L));
        run.setTargetGraphRevision(1L);
        run.setVersion(1);
        return run;
    }

    private AigcProjectView project() {
        return new AigcProjectView(
                1L,
                2L,
                3L,
                "视频项目",
                AigcProjectLifecycle.CREATING,
                1,
                null,
                "social",
                null,
                "standard",
                "manual",
                List.of(),
                List.of(),
                null,
                BigDecimal.ZERO,
                null,
                "项目简报",
                null,
                7L);
    }
}
