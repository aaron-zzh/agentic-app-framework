package com.xuejiai.aaf.module.developer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.developer.domain.DeveloperTokenAccount;

import jakarta.persistence.LockModeType;

public interface DeveloperTokenAccountRepository extends JpaRepository<DeveloperTokenAccount, Long> {

    Optional<DeveloperTokenAccount> findByDeveloperId(Long developerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM DeveloperTokenAccount a WHERE a.developerId = :developerId")
    Optional<DeveloperTokenAccount> findByDeveloperIdForUpdate(@Param("developerId") Long developerId);
}
