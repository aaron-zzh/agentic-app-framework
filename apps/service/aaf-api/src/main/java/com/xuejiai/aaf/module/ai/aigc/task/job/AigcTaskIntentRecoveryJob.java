package com.xuejiai.aaf.module.ai.aigc.task.job;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskExecutor;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskSubmittingRecoveryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 恢复未获得提交结果的 durable Task intent。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigcTaskIntentRecoveryJob {

    private final AigcTaskRepository repository;
    private final AigcTaskExecutor executor;
    private final AigcTaskSubmittingRecoveryService submittingRecoveryService;

    @OrgIgnore
    @Scheduled(fixedDelayString = "${aaf.aigc.task-intent-recovery-delay-ms:30000}")
    public void recover() {
        repository
                .findRecoverableIntents()
                .forEach(
                        task -> {
                            log.info(
                                    "[AigcTaskIntentRecoveryJob] 恢复 PREPARED Task intent: taskId={}",
                                    task.getId());
                            executor.resumeIntent(task.getId());
                        });
        var now = LocalDateTime.now();
        repository
                .findStaleSubmitting(now)
                .forEach(
                        task -> {
                            log.warn(
                                    "[AigcTaskIntentRecoveryJob] SUBMITTING 崩溃转人工对账: taskId={}, providerKey={}",
                                    task.getId(),
                                    task.getProviderKey());
                            submittingRecoveryService.markNeedsReconciliation(task.getId(), now);
                        });
    }
}
