package com.xuejiai.aaf.module.ai.aigc.image.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.image.domain.BatchGenerationTask;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchTaskStatus;

/** 批量生成任务仓储。 */
public interface BatchGenerationTaskRepository extends JpaRepository<BatchGenerationTask, Long> {

    List<BatchGenerationTask> findByUserId(Long userId);

    List<BatchGenerationTask> findByStatus(BatchTaskStatus status);
}
