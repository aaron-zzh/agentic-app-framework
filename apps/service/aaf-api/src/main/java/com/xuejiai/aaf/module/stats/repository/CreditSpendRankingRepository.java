package com.xuejiai.aaf.module.stats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.xuejiai.aaf.module.stats.domain.CreditSpendRanking;

/**
 * 积分消耗排行视图 Repository
 *
 * @author AaronZZH & Kiro
 */
@Repository
public interface CreditSpendRankingRepository
        extends JpaRepository<CreditSpendRanking, Long>,
                JpaSpecificationExecutor<CreditSpendRanking> {}
