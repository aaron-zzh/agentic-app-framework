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

    /**
     * 恢复未取得提交结果的 durable Task intent。
     *
     * <p>{@code PREPARED} 现在同时是并发窗口的排队状态，因此本扫描既补跑中断任务，也是"槽位释放事件丢失"时的兜底推进；
     * 真实放行仍由认领事务按订阅并发窗口判定，重复扫描不会超发。
     */
    @OrgIgnore
    @Scheduled(fixedDelayString = "${aaf.aigc.task-intent-recovery-delay-ms:30000}")
    public void recover() {
        repository
                .findRecoverableIntents()
                .forEach(
                        task -> {
                            log.debug(
                                    "[AigcTaskIntentRecoveryJob] 尝试启动排队中的 Task intent: taskId={}",
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
