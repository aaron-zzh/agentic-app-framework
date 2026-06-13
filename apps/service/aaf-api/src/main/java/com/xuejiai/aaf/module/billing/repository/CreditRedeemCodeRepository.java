package com.xuejiai.aaf.module.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.billing.domain.CreditRedeemCode;

import jakarta.persistence.LockModeType;

public interface CreditRedeemCodeRepository
        extends JpaRepository<CreditRedeemCode, Long>, JpaSpecificationExecutor<CreditRedeemCode> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditRedeemCode c WHERE c.codeHash = :codeHash AND c.deleted = false")
    Optional<CreditRedeemCode> findByCodeHashForUpdate(@Param("codeHash") String codeHash);
}
