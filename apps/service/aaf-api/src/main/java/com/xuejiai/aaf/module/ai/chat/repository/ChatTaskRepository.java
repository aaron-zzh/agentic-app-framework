package com.xuejiai.aaf.module.ai.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;

public interface ChatTaskRepository extends JpaRepository<ChatTask, Long> {

    /** 获取会话下所有任务（按优先级升序、排序序号升序） */
    List<ChatTask> findBySessionIdAndDeletedFalseOrderByPriorityAscSortOrderAsc(Long sessionId);

    /** 获取会话下待处理的下一个任务 */
    Optional<ChatTask> findFirstBySessionIdAndStatusAndDeletedFalseOrderByPriorityAscSortOrderAsc(
            Long sessionId, String status);

    /** 统计会话下指定状态的任务数 */
    long countBySessionIdAndStatusAndDeletedFalse(Long sessionId, String status);
}
