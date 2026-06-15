package com.xuejiai.aaf.framework.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 任务执行记录仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    Page<TaskExecution> findByTaskNameContainingOrTaskTypeContaining(
            String taskName, String taskType, Pageable pageable);

    Page<TaskExecution> findByStatus(String status, Pageable pageable);
}
