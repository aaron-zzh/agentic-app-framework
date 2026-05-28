/**
 * Agent 定义仓库。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Agent 定义数据访问。 */
public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, Long> {

    Optional<AgentDefinition> findByAgentId(String agentId);

    List<AgentDefinition> findByStatus(String status);

    Page<AgentDefinition> findByStatus(String status, Pageable pageable);

    List<AgentDefinition> findByStatusAndCapabilitiesContaining(String status, String capability);
}
