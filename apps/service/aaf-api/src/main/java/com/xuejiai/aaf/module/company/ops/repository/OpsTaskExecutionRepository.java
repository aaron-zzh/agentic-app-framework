package com.xuejiai.aaf.module.company.ops.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.company.ops.domain.OpsTaskExecution;

public interface OpsTaskExecutionRepository extends JpaRepository<OpsTaskExecution, Long> {

    Page<OpsTaskExecution> findByTaskIdOrderByCreateTimeDesc(Long taskId, Pageable pageable);
}
