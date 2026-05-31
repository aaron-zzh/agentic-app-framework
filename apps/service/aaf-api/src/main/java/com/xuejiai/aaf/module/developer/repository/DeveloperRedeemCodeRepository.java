package com.xuejiai.aaf.module.developer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.developer.domain.DeveloperRedeemCode;

import jakarta.persistence.LockModeType;

public interface DeveloperRedeemCodeRepository extends JpaRepository<DeveloperRedeemCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DeveloperRedeemCode c WHERE c.codeHash = :codeHash")
    Optional<DeveloperRedeemCode> findByCodeHashForUpdate(@Param("codeHash") String codeHash);
}
