package com.xuejiai.aaf.module.system.dashboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.dashboard.domain.DashboardWidget;

/** 仪表盘组件仓储。 */
public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, Long> {

    List<DashboardWidget> findByDashboardIdOrderBySortOrder(Long dashboardId);

    void deleteByDashboardId(Long dashboardId);
}
