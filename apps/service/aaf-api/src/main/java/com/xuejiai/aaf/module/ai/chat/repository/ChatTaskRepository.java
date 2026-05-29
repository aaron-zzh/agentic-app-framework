package com.xuejiai.aaf.module.ai.chat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;

public interface ChatTaskRepository extends JpaRepository<ChatTask, Long> {

    /** 获取会话下所有任务（按优先级升序、排序序号升序） */
    List<ChatTask> findBySessionIdAndDeletedFalseOrderByPriorityAscSortOrderAsc(Long sessionId);

    /** 获取会话下待处理的下一个任务（已到期或无定时） */
    @Query("""
            SELECT t FROM ChatTask t
            WHERE t.sessionId = :sessionId AND t.status = 'pending' AND t.deleted = false
              AND (t.scheduledAt IS NULL OR t.scheduledAt <= CURRENT_TIMESTAMP)
            ORDER BY t.priority ASC, t.sortOrder ASC
            LIMIT 1""")
    Optional<ChatTask> findNextPending(Long sessionId);

    /** 查找所有到期的待处理任务（定时调度用） */
    @Query("""
            SELECT t FROM ChatTask t
            WHERE t.status = 'pending' AND t.deleted = false
              AND t.scheduledAt IS NOT NULL AND t.scheduledAt <= :now
            ORDER BY t.scheduledAt ASC, t.priority ASC""")
    List<ChatTask> findDueTasks(LocalDateTime now);

    /** 统计会话下指定状态的任务数 */
    long countBySessionIdAndStatusAndDeletedFalse(Long sessionId, String status);
}
