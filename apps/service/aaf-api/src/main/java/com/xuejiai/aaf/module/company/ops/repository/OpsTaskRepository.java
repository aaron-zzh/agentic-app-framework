package com.xuejiai.aaf.module.company.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.company.ops.domain.OpsTask;

public interface OpsTaskRepository extends JpaRepository<OpsTask, Long> {

    List<OpsTask> findByEnabledTrue();

    List<OpsTask> findByCategory(String category);
}
