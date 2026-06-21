package com.xuejiai.aaf.framework.intelligent.agent.trace;

import java.time.Instant;

import org.springframework.data.neo4j.core.schema.*;

import lombok.Getter;
import lombok.Setter;

/** Agent 间调用关系（Neo4j）。 */
@Getter
@Setter
@RelationshipProperties
public class AgentInvocationRelation {

    @RelationshipId @GeneratedValue private String id;

    /** 调用次数（聚合） */
    private int count;

    /** 最近调用时间 */
    private Instant lastAt;

    /** 平均耗时（毫秒） */
    private long avgDurationMs;

    /** 目标 Agent */
    @TargetNode private AgentGraphNode target;
}
