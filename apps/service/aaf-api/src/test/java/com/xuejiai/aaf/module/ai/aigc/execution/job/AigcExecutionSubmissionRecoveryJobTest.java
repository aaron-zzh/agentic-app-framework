package com.xuejiai.aaf.module.ai.aigc.execution.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionSubmission;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionSubmissionRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcActionCommandService;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcExecutionSubmissionService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcExecutionSubmissionRecoveryJobTest extends BaseMockitoUnitTest {

    @Mock private AigcExecutionSubmissionRepository repository;
    @Mock private AigcExecutionSubmissionService submissionService;
    @Mock private AigcActionCommandService commandService;
    @Mock private PermissionExecutionService permissionExecutionService;
    @InjectMocks private AigcExecutionSubmissionRecoveryJob job;

    @AfterEach
    void clearScope() {
        OrgContext.clear();
    }

    @Test
    @DisplayName("Given 跨 scope 待恢复 submission When 定时恢复 Then 仅扫描忽略隔离且逐条恢复正常 scope")
    void should_ignore_org_only_while_scanning_and_restore_each_submission_in_its_scope() {
        // 准备参数
        var submission = new AigcExecutionSubmission();
        submission.setId(1L);
        submission.setOwnerId(7L);
        submission.setOrgId(8L);
        submission.setWorkspaceId(9L);
        var command = mock(AigcActionCommand.class);
        when(repository.findByStatusInOrderByIdAsc(any()))
                .thenAnswer(
                        ignored -> {
                            assertThat(OrgContext.isIgnore()).isTrue();
                            return List.of(submission);
                        });
        when(submissionService.toCommand(submission)).thenReturn(command);
        when(submissionService.isRecoveryDue(eq(submission), any())).thenReturn(true);
        doAnswer(
                        invocation -> {
                            invocation.getArgument(2, Runnable.class).run();
                            return null;
                        })
                .when(permissionExecutionService)
                .runAsOwner(eq(7L), eq("aigc-execution-recovery"), any(Runnable.class));
        doAnswer(
                        ignored -> {
                            assertThat(OrgContext.isIgnore()).isFalse();
                            assertThat(OrgContext.getCurrentOrgId()).isEqualTo(8L);
                            assertThat(OrgContext.getCurrentWorkspaceId()).isEqualTo(9L);
                            return null;
                        })
                .when(commandService)
                .resumeSubmission(1L, command);

        // 调用
        job.recover();

        // 断言
        verify(commandService).resumeSubmission(1L, command);
        verify(submissionService).clearRecoveryFailure(1L);
        assertThat(OrgContext.isIgnore()).isFalse();
        assertThat(OrgContext.getCurrentOrgId()).isNull();
        assertThat(OrgContext.getCurrentWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("Given recovery 遇到瞬态异常 When 定时恢复 Then 记录退避并保留可恢复状态")
    void should_record_retryable_failure_without_terminalizing_submission() {
        // 准备参数
        var submission = submission();
        var command = mock(AigcActionCommand.class);
        prepareRecovery(submission, command);
        doThrow(new IllegalStateException("数据库暂不可用"))
                .when(commandService)
                .resumeSubmission(1L, command);

        // 调用
        job.recover();

        // 断言
        verify(submissionService).recordRetryableRecoveryFailure(1L, "数据库暂不可用");
        verify(commandService, never()).failRecovery(any(), any());
    }

    @Test
    @DisplayName("Given recovery 遇到确定业务错误 When 定时恢复 Then 才标记 RECOVERY_FAILED")
    void should_terminalize_only_permanent_business_failure() {
        // 准备参数
        var submission = submission();
        var command = mock(AigcActionCommand.class);
        prepareRecovery(submission, command);
        doThrow(new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目状态不可执行"))
                .when(commandService)
                .resumeSubmission(1L, command);

        // 调用
        job.recover();

        // 断言
        verify(commandService).failRecovery(1L, "项目状态不可执行");
        verify(submissionService, never()).recordRetryableRecoveryFailure(any(), any());
    }

    private AigcExecutionSubmission submission() {
        var submission = new AigcExecutionSubmission();
        submission.setId(1L);
        submission.setOwnerId(7L);
        submission.setOrgId(8L);
        submission.setWorkspaceId(9L);
        return submission;
    }

    private void prepareRecovery(AigcExecutionSubmission submission, AigcActionCommand command) {
        when(repository.findByStatusInOrderByIdAsc(any())).thenReturn(List.of(submission));
        when(submissionService.isRecoveryDue(eq(submission), any())).thenReturn(true);
        when(submissionService.toCommand(submission)).thenReturn(command);
        doAnswer(
                        invocation -> {
                            invocation.getArgument(2, Runnable.class).run();
                            return null;
                        })
                .when(permissionExecutionService)
                .runAsOwner(eq(7L), eq("aigc-execution-recovery"), any(Runnable.class));
    }
}
