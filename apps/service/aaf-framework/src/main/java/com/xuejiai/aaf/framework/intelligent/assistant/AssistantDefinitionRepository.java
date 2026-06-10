/**
 * Assistant 定义仓储。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantDefinitionRepository extends JpaRepository<AssistantDefinition, Long> {

    Page<AssistantDefinition> findByUserId(Long userId, Pageable pageable);

    List<AssistantDefinition> findByUserIdAndStatus(Long userId, String status);
}
