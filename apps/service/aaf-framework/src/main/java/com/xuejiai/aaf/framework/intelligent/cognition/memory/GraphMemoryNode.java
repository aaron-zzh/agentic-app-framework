/**
 * 图谱记忆节点（Neo4j）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.neo4j.core.schema.*;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import lombok.Getter;
import lombok.Setter;

/** 图谱记忆中的实体节点，参考 Graphiti 双时态模型。 */
@Getter
@Setter
@Node("MemoryEntity")
public class GraphMemoryNode {

    @Id @GeneratedValue(UUIDStringGenerator.class) private String id;

    /** 实体名称 */
    private String name;

    /** 实体类型（person / concept / event / location） */
    private String entityType;

    /** 所属用户 */
    private Long userId;

    /** 摘要描述 */
    private String summary;

    /** 事件时间（双时态） */
    private Instant eventTime;

    /** 写入时间（双时态） */
    private Instant createdAt;

    /** 关系列表 */
    @Relationship(type = "RELATES_TO")
    private Set<GraphMemoryRelation> relations = new HashSet<>();
}
