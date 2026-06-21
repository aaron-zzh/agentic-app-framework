package com.xuejiai.aaf.module.system.dashboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.dashboard.domain.DashboardPreset;

/**
 * 仪表盘预设仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface DashboardPresetRepository extends JpaRepository<DashboardPreset, Long> {

    /** 查询所有启用的预设，按排序号升序 */
    List<DashboardPreset> findByStatusAndDeletedFalseOrderBySortOrderAsc(Short status);
}
