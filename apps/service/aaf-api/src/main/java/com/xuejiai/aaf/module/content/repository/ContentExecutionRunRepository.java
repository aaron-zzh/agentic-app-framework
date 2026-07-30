package com.xuejiai.aaf.module.content.repository;

import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;

/**
 * 执行记录仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface ContentExecutionRunRepository extends CrudEntityRepository<ContentExecutionRun> {

    long countByProjectIdAndStatus(Long projectId, String status);

    Optional<ContentExecutionRun> findFirstByAigcTaskIdOrderByIdDesc(Long aigcTaskId);
}
