package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.neo4j.core.schema.*;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import lombok.Getter;
import lombok.Setter;

/** Agent 协作拓扑节点（Neo4j）。 */
@Getter
@Setter
@Node("AgentNode")
public class AgentGraphNode {

    @Id
    @GeneratedValue(UUIDStringGenerator.class)
    private String id;

    private String agentId;

    private String name;

    private String type;

    private Instant createdAt;

    @Relationship(type = "INVOKED")
    private Set<AgentInvocationRelation> invocations = new HashSet<>();
}
