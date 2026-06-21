package com.xuejiai.aaf.module.stats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.xuejiai.aaf.module.stats.domain.UserContributionStats;

/**
 * 用户贡献统计视图 Repository
 *
 * @author AaronZZH & Kiro
 */
@Repository
public interface UserContributionStatsRepository
        extends JpaRepository<UserContributionStats, Long>,
                JpaSpecificationExecutor<UserContributionStats> {}
