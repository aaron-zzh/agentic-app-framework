package com.xuejiai.aaf.module.developer.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.developer.domain.DeveloperTokenTransaction;

public interface DeveloperTokenTransactionRepository
        extends JpaRepository<DeveloperTokenTransaction, Long> {

    Page<DeveloperTokenTransaction> findByDeveloperId(Long developerId, Pageable pageable);
}
