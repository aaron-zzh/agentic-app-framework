package com.xuejiai.aaf.module.ai.aigc.execution.repository;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;

public interface AigcExecutionRunRepository extends CrudEntityRepository<AigcExecutionRun> {

    long countByProjectIdAndStatus(Long projectId, String status);

    List<AigcExecutionRun> findByProjectIdAndStatusIn(Long projectId, List<String> statuses);

    Optional<AigcExecutionRun> findFirstByRetryOfRunIdOrderByRetryCountDesc(Long retryOfRunId);
}
