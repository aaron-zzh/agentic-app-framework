package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 执行运行记录仓储。 */
public interface ExecutionRunRepository extends JpaRepository<ExecutionRun, Long> {

    Optional<ExecutionRun> findByExecutionId(String executionId);

    List<ExecutionRun> findByUserIdOrderByStartedAtDesc(Long userId, org.springframework.data.domain.Pageable pageable);

    List<ExecutionRun> findByStatusAndAgentId(ExecutionStatus status, String agentId);
}
