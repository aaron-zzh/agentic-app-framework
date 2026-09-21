package com.xuejiai.aaf.module.ai.aigc.task.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskStatusEnum;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 并发槽位释放后的排队派发。
 *
 * <p>只负责"挑出该用户最早的排队任务并再次尝试认领"，是否真的放行仍由 {@link AigcTaskClaimService} 在其原子准入事务内判定，
 * 因此重复派发、并发派发与恢复扫描三者叠加也不会超出订阅并发窗口，也不会重复提交供应商。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigcTaskQueueDispatcher {

    private final AigcTaskRepository taskRepository;
    private final AigcTaskExecutor taskExecutor;

    @OrgIgnore
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void dispatchNext(Long userId) {
        if (userId == null) {
            return;
        }
        taskRepository
                .findFirstByUserIdAndStatusAndDeletedFalseOrderByIdAsc(
                        userId, AigcTaskStatusEnum.PREPARED.getCode())
                .ifPresent(
                        task -> {
                            log.debug(
                                    "[AigcTaskQueue] 槽位释放，尝试启动排队任务: userId={}, taskId={}",
                                    userId,
                                    task.getId());
                            taskExecutor.resumeIntent(task.getId());
                        });
    }
}
