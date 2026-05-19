package com.xuejiai.aaf.module.system.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.ScheduledTask;

/** 计划任务仓储。 */
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

    /** 查询所有活跃任务 */
    List<ScheduledTask> findByStatus(String status);
}
