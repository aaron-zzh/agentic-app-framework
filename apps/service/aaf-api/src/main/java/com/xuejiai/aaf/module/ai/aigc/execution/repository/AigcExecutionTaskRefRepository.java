package com.xuejiai.aaf.module.ai.aigc.execution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionTaskRef;

public interface AigcExecutionTaskRefRepository extends JpaRepository<AigcExecutionTaskRef, Long> {

    List<AigcExecutionTaskRef> findByExecutionRunIdAndDeletedFalseOrderBySortOrderAsc(
            Long executionRunId);

    Optional<AigcExecutionTaskRef> findFirstByTaskIdAndDeletedFalseOrderByIdDesc(Long taskId);

    List<AigcExecutionTaskRef> findByExecutionRunIdInAndDeletedFalse(List<Long> executionRunIds);
}
