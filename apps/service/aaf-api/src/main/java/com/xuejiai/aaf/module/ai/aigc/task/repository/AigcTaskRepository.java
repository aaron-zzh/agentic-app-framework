package com.xuejiai.aaf.module.ai.aigc.task.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;

/**
 * AIGC 统一任务 Repository。
 *
 * @author AaronZZH
 */
public interface AigcTaskRepository
        extends JpaRepository<AigcTask, Long>, JpaSpecificationExecutor<AigcTask> {

    /** 按用户分页查询（最新在前） */
    Page<AigcTask> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    /** 按第三方任务 ID 查找 */
    Optional<AigcTask> findByTaskId(String taskId);

    /** 查询指定状态的任务列表 */
    List<AigcTask> findByStatus(String status);

    /** 查询指定状态且创建时间早于给定时间的任务（用于检测卡住的任务） */
    List<AigcTask> findByStatusAndCreateTimeBefore(String status, LocalDateTime before);

    /** 按状态和任务类型查询 */
    List<AigcTask> findByStatusAndType(String status, String type);
}
