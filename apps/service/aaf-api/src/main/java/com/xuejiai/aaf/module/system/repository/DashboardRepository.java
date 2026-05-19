package com.xuejiai.aaf.module.system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.Dashboard;

/** 仪表盘仓储。 */
public interface DashboardRepository extends JpaRepository<Dashboard, Long> {

    List<Dashboard> findByOwnerIdOrderByIsDefaultDescCreateTimeDesc(Long ownerId);

    Optional<Dashboard> findByOwnerIdAndIsDefaultTrue(Long ownerId);
}
