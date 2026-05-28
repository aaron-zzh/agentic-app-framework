package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 执行运行记录仓储。 */
public interface ExecutionRunRepository extends JpaRepository<ExecutionRun, Long> {

    Optional<ExecutionRun> findByExecutionId(String executionId);

    List<ExecutionRun> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    List<ExecutionRun> findByStatusAndAgentId(ExecutionStatus status, String agentId);

    Page<ExecutionRun> findByAgentId(String agentId, Pageable pageable);
}
