package com.xuejiai.aaf.module.ai.aigc.task;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AIGC 统一任务 Repository。
 *
 * @author Kiro
 */
public interface AigcTaskRepository extends JpaRepository<AigcTask, Long> {

    /** 按用户分页查询（最新在前） */
    Page<AigcTask> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    /** 按第三方任务 ID 查找 */
    Optional<AigcTask> findByTaskId(String taskId);

    /** 查询指定状态的任务列表 */
    List<AigcTask> findByStatus(String status);
}
