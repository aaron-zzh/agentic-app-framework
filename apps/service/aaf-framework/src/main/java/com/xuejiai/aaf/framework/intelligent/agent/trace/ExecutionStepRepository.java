package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 执行步骤仓储。 */
public interface ExecutionStepRepository extends JpaRepository<ExecutionStep, Long> {

    List<ExecutionStep> findByRunIdOrderByStepIndex(Long runId);
}
