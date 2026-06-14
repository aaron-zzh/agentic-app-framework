package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 知识图谱实体节点，参考 Graphiti 双时态模型 */
@Node("KnowledgeEntity")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeEntity {

    /** 业务 ID，由序列生成器分配，不使用 Neo4j 内部 elementId */
    @Id
    @GeneratedValue(UUIDStringGenerator.class)
    private String id;

    /** 实体名称 */
    private String name;

    /** 实体类型（Person/Organization/Concept/Event 等） */
    private String type;

    /** 描述 */
    private String description;

    /** 来源文档ID */
    private Long sourceDocumentId;

    /** 所属知识库 */
    private Long knowledgeBaseId;

    /** 动态属性 */
    private Map<String, Object> properties = new HashMap<>();

    /** 出发关系 */
    @Relationship(type = "RELATES_TO", direction = Relationship.Direction.OUTGOING)
    private List<KnowledgeRelation> relations = new ArrayList<>();

    private Instant createdAt;

    private Instant updatedAt;
}
