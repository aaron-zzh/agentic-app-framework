package com.xuejiai.aaf.module.pay.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.pay.domain.CreditTokenRule;

/** 积分转 Token 规则仓储 */
public interface CreditTokenRuleRepository extends JpaRepository<CreditTokenRule, Long> {

    /** 查询当前生效的规则，按优先级排序 */
    @Query(
            "SELECT r FROM CreditTokenRule r WHERE r.status = 'ENABLED' "
                    + "AND (r.effectiveFrom IS NULL OR r.effectiveFrom <= :now) "
                    + "AND (r.effectiveTo IS NULL OR r.effectiveTo >= :now) "
                    + "AND r.deleted = false ORDER BY r.priority ASC")
    List<CreditTokenRule> findEffectiveRules(LocalDateTime now);
}
