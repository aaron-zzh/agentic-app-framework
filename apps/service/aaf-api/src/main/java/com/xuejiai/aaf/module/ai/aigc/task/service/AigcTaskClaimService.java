package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskStatusEnum;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;

import lombok.RequiredArgsConstructor;

/** 以独立短事务原子认领仍处于 PENDING 的任务。 */
@Service
@RequiredArgsConstructor
public class AigcTaskClaimService {

    private final AigcTaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AigcTask> claimPending(Long taskId) {
        var task = taskRepository.findLockedById(taskId).orElse(null);
        if (task == null || !AigcTaskStatusEnum.PENDING.getCode().equals(task.getStatus())) {
            return Optional.empty();
        }
        task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
        task.setUpdateTime(LocalDateTime.now());
        task.setVersion(task.getVersion() + 1);
        return Optional.of(taskRepository.saveAndFlush(task));
    }
}
