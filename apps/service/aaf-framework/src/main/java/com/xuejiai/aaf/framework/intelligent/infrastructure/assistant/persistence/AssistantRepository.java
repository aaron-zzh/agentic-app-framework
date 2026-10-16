package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantRepository extends JpaRepository<AssistantEntity, Long> {

    Optional<AssistantEntity> findByCodeAndStatus(String code, String status);

    Optional<AssistantEntity> findFirstByUserIdAndIsDefaultTrueAndStatusOrderByIdAsc(
            Long userId, String status);

    /** 当前用户可用的 Assistant：本人拥有（userId 匹配）或全局共享（userId=0 的 SYSTEM_MANAGED）。 */
    List<AssistantEntity> findByUserIdInAndStatusOrderByIdAsc(List<Long> userIds, String status);

    /** 解析助理绑定知识库的 stable ID；知识库已删除或停用时返回空。 */
    @Query(
            value =
                    "SELECT stable_id FROM ai_knowledge_base WHERE id = :id AND deleted = false AND status = 0",
            nativeQuery = true)
    Optional<UUID> findActiveKnowledgeBaseStableId(@Param("id") Long id);
}
