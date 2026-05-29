package com.xuejiai.aaf.module.ai.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.chat.domain.TaskCheckpoint;

public interface TaskCheckpointRepository extends JpaRepository<TaskCheckpoint, Long> {

    /** 获取执行实例的最新检查点 */
    Optional<TaskCheckpoint> findFirstByExecutionIdOrderByStepIndexDesc(Long executionId);
}
