package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantRepository extends JpaRepository<AssistantEntity, Long> {

    Optional<AssistantEntity> findByCodeAndStatus(String code, String status);

    Optional<AssistantEntity> findFirstByUserIdAndIsDefaultTrueAndStatusOrderByIdAsc(
            Long userId, String status);
}
