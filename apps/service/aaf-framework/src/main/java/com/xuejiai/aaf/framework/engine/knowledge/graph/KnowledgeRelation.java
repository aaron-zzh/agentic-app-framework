package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.time.Instant;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 知识图谱关系属性，附着在 RELATES_TO 关系上 */
@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeRelation {

    @RelationshipId @GeneratedValue private Long id;

    /** 关系类型（如 IS_A / PART_OF / WORKS_AT 等） */
    private String type;

    /** 权重 */
    private Double weight;

    /** 置信度 */
    private Double confidence;

    /** 来源文档ID */
    private Long sourceDocumentId;

    /** 目标节点 */
    @TargetNode private KnowledgeEntity target;

    private Instant createdAt;
}
