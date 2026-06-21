package com.xuejiai.aaf.module.brokerage.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.brokerage.domain.BrokerageUser;

public interface BrokerageUserRepository
        extends JpaRepository<BrokerageUser, Long>, JpaSpecificationExecutor<BrokerageUser> {

    Optional<BrokerageUser> findByContactId(Long contactId);

    /** 原子增加冻结金额（佣金冻结时） */
    @Modifying
    @Transactional
    @Query(
            value =
                    "UPDATE brokerage_user SET frozen = frozen + :amount WHERE contact_id = :contactId AND deleted = false",
            nativeQuery = true)
    int addFrozen(@Param("contactId") Long contactId, @Param("amount") long amount);

    /** 原子增加可用余额（立即有效的佣金） */
    @Modifying
    @Transactional
    @Query(
            value =
                    "UPDATE brokerage_user SET balance = balance + :amount WHERE contact_id = :contactId AND deleted = false",
            nativeQuery = true)
    int addBalance(@Param("contactId") Long contactId, @Param("amount") long amount);

    /** 原子减少冻结金额（退款冲回/取消冻结时） */
    @Modifying
    @Transactional
    @Query(
            value =
                    "UPDATE brokerage_user SET frozen = GREATEST(0, frozen - :amount) WHERE contact_id = :contactId AND deleted = false",
            nativeQuery = true)
    int reduceFrozen(@Param("contactId") Long contactId, @Param("amount") long amount);

    /** 原子减少可用余额（退款冲回已解冻佣金时） */
    @Modifying
    @Transactional
    @Query(
            value =
                    "UPDATE brokerage_user SET balance = GREATEST(0, balance - :amount) WHERE contact_id = :contactId AND deleted = false",
            nativeQuery = true)
    int reduceBalance(@Param("contactId") Long contactId, @Param("amount") long amount);

    /** 原子解冻：balance += amount, frozen -= amount（定时任务解冻用） */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            UPDATE brokerage_user
            SET balance = balance + :amount,
                frozen  = GREATEST(0, frozen - :amount)
            WHERE contact_id = :contactId AND deleted = false
            """,
            nativeQuery = true)
    int addBalanceAndReduceFrozen(@Param("contactId") Long contactId, @Param("amount") long amount);
}
