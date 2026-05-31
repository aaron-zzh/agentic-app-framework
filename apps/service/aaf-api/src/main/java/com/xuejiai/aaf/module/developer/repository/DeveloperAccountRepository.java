package com.xuejiai.aaf.module.developer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.developer.domain.DeveloperAccount;

public interface DeveloperAccountRepository extends JpaRepository<DeveloperAccount, Long> {

    Optional<DeveloperAccount> findByUserId(Long userId);

    Optional<DeveloperAccount> findByDeveloperCode(String developerCode);
}
