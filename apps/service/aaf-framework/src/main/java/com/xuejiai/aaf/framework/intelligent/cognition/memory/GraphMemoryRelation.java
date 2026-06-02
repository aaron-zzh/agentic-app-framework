/**
 * 图谱记忆关系。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Instant;

import org.springframework.data.neo4j.core.schema.*;

import lombok.Getter;
import lombok.Setter;

/** 实体间的关系，携带时序信息。 */
@Getter
@Setter
@RelationshipProperties
public class GraphMemoryRelation {

    @Id @GeneratedValue private Long id;

    /** 关系类型描述（如 "认识"、"参与"、"属于"） */
    private String relationType;

    /** 关系权重 0.0~1.0 */
    private Double weight;

    /** 边的自然语言描述——边语义化载体 */
    private String edgeText;

    /** 边描述的向量表示——检索时与 query 向量比较 */
    private float[] edgeEmbedding;

    /** 事件时间 */
    private Instant eventTime;

    /** 目标节点 */
    @TargetNode private GraphMemoryNode target;
}
