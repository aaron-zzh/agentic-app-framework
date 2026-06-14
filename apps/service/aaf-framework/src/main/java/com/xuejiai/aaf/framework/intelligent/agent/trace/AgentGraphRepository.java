package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;

/** Agent 协作拓扑 Neo4j 仓储。 */
public interface AgentGraphRepository extends Neo4jRepository<AgentGraphNode, String> {

    Optional<AgentGraphNode> findByAgentId(String agentId);
}
