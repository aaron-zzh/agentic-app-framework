package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantRepository extends JpaRepository<AssistantEntity, Long> {

    Optional<AssistantEntity> findByCodeAndStatus(String code, String status);

    Optional<AssistantEntity> findFirstByUserIdAndIsDefaultTrueAndStatusOrderByIdAsc(
            Long userId, String status);

    /** 当前用户可用的 Assistant：本人拥有（userId 匹配）或全局共享（userId=0 的 SYSTEM_MANAGED）。 */
    List<AssistantEntity> findByUserIdInAndStatusOrderByIdAsc(List<Long> userIds, String status);
}
