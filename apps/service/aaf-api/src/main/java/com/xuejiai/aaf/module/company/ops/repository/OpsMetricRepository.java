package com.xuejiai.aaf.module.company.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.company.ops.domain.OpsMetric;

public interface OpsMetricRepository extends JpaRepository<OpsMetric, Long> {

    List<OpsMetric> findByCodeOrderByRecordedAtDesc(String code);
}
