package com.xuejiai.aaf.module.ai.aigc.execution.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionSubmission;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionSubmissionRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcActionCommandService;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcExecutionSubmissionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 只按 durable 状态推进未完成 saga，不按墙钟释放 reservation。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigcExecutionSubmissionRecoveryJob {

    private static final List<String> RECOVERABLE_STATUSES =
            List.of("INTENT_RECORDED", "PREPARING", "RESERVED", "ROOT_CREATED", "BOUND");

    private final AigcExecutionSubmissionRepository repository;
    private final AigcExecutionSubmissionService submissionService;
    private final AigcActionCommandService commandService;
    private final PermissionExecutionService permissionExecutionService;

    @Scheduled(fixedDelayString = "${aaf.aigc.execution-recovery-delay:30000}")
    public void recover() {
        var submissions =
                OrgContext.runIgnoring(
                        () -> repository.findByStatusInOrderByIdAsc(RECOVERABLE_STATUSES));
        var now = LocalDateTime.now();
        submissions.stream()
                .filter(submission -> submissionService.isRecoveryDue(submission, now))
                .forEach(this::recoverOne);
    }

    private void recoverOne(AigcExecutionSubmission submission) {
        permissionExecutionService.runAsOwner(
                submission.getOwnerId(),
                "aigc-execution-recovery",
                () ->
                        withOrgContext(
                                submission.getOrgId(),
                                submission.getWorkspaceId(),
                                () -> resumeOrFail(submission)));
    }

    private void resumeOrFail(AigcExecutionSubmission submission) {
        try {
            commandService.resumeSubmission(
                    submission.getId(), submissionService.toCommand(submission));
            submissionService.clearRecoveryFailure(submission.getId());
        } catch (RuntimeException error) {
            var message =
                    error.getMessage() == null
                            ? "execution submission 恢复失败"
                            : error.getMessage();
            var businessError = permanentBusinessError(error);
            if (businessError != null) {
                log.warn(
                        "恢复 AIGC execution submission 遇到确定业务错误并终态化: submissionId={}",
                        submission.getId(),
                        error);
                commandService.failRecovery(submission.getId(), businessError.getMessage());
                return;
            }
            log.warn(
                    "恢复 AIGC execution submission 遇到瞬态错误，保留可恢复状态: submissionId={}",
                    submission.getId(),
                    error);
            submissionService.recordRetryableRecoveryFailure(submission.getId(), message);
        }
    }

    private BusinessException permanentBusinessError(Throwable error) {
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof BusinessException businessError
                    && businessError.getHttpStatus() >= 400
                    && businessError.getHttpStatus() < 500
                    && businessError.getHttpStatus() != 409
                    && businessError.getHttpStatus() != 429) {
                return businessError;
            }
        }
        return null;
    }

    private void withOrgContext(Long orgId, Long workspaceId, Runnable action) {
        var previousOrgId = OrgContext.getCurrentOrgId();
        var previousWorkspaceId = OrgContext.getCurrentWorkspaceId();
        try {
            OrgContext.setCurrentOrgId(orgId);
            OrgContext.setCurrentWorkspaceId(workspaceId);
            action.run();
        } finally {
            OrgContext.setCurrentOrgId(previousOrgId);
            OrgContext.setCurrentWorkspaceId(previousWorkspaceId);
        }
    }
}
