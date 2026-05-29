package com.xuejiai.aaf.module.company.ops.repository;

import java.util.List;

import com.xuejiai.aaf.module.company.ops.domain.OpsTask;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsTaskRepository extends JpaRepository<OpsTask, Long> {

    List<OpsTask> findByEnabledTrue();

    List<OpsTask> findByCategory(String category);
}
