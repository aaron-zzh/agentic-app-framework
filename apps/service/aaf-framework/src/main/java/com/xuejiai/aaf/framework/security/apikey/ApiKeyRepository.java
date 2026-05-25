package com.xuejiai.aaf.framework.security.apikey;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** API Key 仓储。 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHashAndEnabledTrue(String keyHash);

    List<ApiKey> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ApiKey> findAllByOrderByCreatedAtDesc();
}
