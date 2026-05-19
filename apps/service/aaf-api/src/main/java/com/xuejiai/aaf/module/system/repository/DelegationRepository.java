package com.xuejiai.aaf.module.system.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.domain.Delegation;

/** 审批委托仓储。 */
public interface DelegationRepository
        extends JpaRepository<Delegation, Long>, JpaSpecificationExecutor<Delegation> {

    /** 查询指定委托人当前生效的委托 */
    Optional<Delegation> findByDelegatorIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long delegatorId, String status, LocalDateTime now1, LocalDateTime now2);
}
