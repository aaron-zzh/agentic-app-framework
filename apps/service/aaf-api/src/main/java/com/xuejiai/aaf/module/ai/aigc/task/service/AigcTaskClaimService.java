package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskStatusEnum;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.billing.api.ParallelTaskQuotaApi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 以独立短事务原子认领排队中的任务。
 *
 * <p>手动与 Assistant 提交的专业任务都先持久化为 {@code PREPARED} 并共用本认领入口，因此订阅并发窗口只需在这一处判定： advisory lock
 * 串行化同一用户的准入 → 读订阅上限 → 数占槽任务 → CAS 认领。窗口已满不是失败，任务保持 {@code PREPARED} 继续排队， 由槽位释放后的派发或恢复扫描再次尝试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigcTaskClaimService {

    private final AigcTaskRepository taskRepository;
    private final ParallelTaskQuotaApi parallelTaskQuota;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AigcTask> claimIntent(Long taskId, String owner) {
        var userId = taskRepository.findQueuedIntentUserId(taskId).orElse(null);
        if (userId == null) {
            return Optional.empty();
        }
        if (!taskRepository.tryLockUserAdmission(userId)) {
            return Optional.empty();
        }
        if (!hasFreeConcurrencySlot(userId, taskId)) {
            return Optional.empty();
        }
        var now = LocalDateTime.now();
        var claimed = taskRepository.claimIntent(taskId, owner, now.plusMinutes(30));
        if (claimed != 1) {
            return Optional.empty();
        }
        return taskRepository.findById(taskId).filter(task -> owner.equals(task.getSubmitOwner()));
    }

    /** 订阅并发窗口判定：只比较"正在提交或运行"的任务数，排队任务不占槽；上限为 -1 表示不限制。 */
    private boolean hasFreeConcurrencySlot(Long userId, Long taskId) {
        var limit = parallelTaskQuota.parallelTaskLimit(userId);
        if (limit < 0) {
            return true;
        }
        var occupied =
                taskRepository.countByUserIdAndStatusInAndDeletedFalse(
                        userId, AigcTaskStatusEnum.CONCURRENCY_OCCUPYING_CODES);
        if (occupied < limit) {
            return true;
        }
        log.debug(
                "[AigcTaskClaim] 并发窗口已满，任务继续排队: userId={}, taskId={}, occupied={}, limit={}",
                userId,
                taskId,
                occupied,
                limit);
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AigcTask> claimCompletion(String providerTaskId, String taskType) {
        if (taskRepository.claimCompletion(providerTaskId, taskType) != 1) {
            return Optional.empty();
        }
        return taskRepository
                .findByProviderTaskId(providerTaskId)
                .filter(task -> "COMPLETING".equals(task.getStatus()))
                .filter(task -> taskType.equals(task.getType()));
    }
}
