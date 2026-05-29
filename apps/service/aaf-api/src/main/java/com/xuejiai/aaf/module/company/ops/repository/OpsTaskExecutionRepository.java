package com.xuejiai.aaf.module.company.ops.repository;

import com.xuejiai.aaf.module.company.ops.domain.OpsTaskExecution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsTaskExecutionRepository extends JpaRepository<OpsTaskExecution, Long> {

    Page<OpsTaskExecution> findByTaskIdOrderByCreateTimeDesc(Long taskId, Pageable pageable);
}
