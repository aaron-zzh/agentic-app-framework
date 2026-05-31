package com.xuejiai.aaf.module.developer.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.developer.domain.DeveloperApiKey;

public interface DeveloperApiKeyRepository extends JpaRepository<DeveloperApiKey, Long> {

    Optional<DeveloperApiKey> findByKeyHashAndEnabledTrue(String keyHash);

    List<DeveloperApiKey> findByDeveloperIdOrderByCreateTimeDesc(Long developerId);
}
