package com.xuejiai.aaf.module.ai.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.chat.domain.TaskEvent;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    /** 获取任务的所有事件（按时间正序） */
    List<TaskEvent> findByTaskIdOrderByCreateTimeAsc(Long taskId);

    /** 获取执行实例的事件 */
    List<TaskEvent> findByExecutionIdOrderByCreateTimeAsc(Long executionId);
}
