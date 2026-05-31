package com.xuejiai.aaf.module.developer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.developer.domain.DeveloperSubscription;

public interface DeveloperSubscriptionRepository
        extends JpaRepository<DeveloperSubscription, Long> {

    Optional<DeveloperSubscription> findByDeveloperIdAndStatus(Long developerId, String status);
}
