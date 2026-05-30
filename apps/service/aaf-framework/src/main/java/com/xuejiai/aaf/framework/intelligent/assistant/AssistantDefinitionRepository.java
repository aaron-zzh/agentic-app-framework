/**
 * Assistant 定义仓储。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantDefinitionRepository extends JpaRepository<AssistantDefinition, Long> {

    Optional<AssistantDefinition> findByAssistantId(String assistantId);

    Page<AssistantDefinition> findByUserId(Long userId, Pageable pageable);

    List<AssistantDefinition> findByUserIdAndStatus(Long userId, String status);
}
