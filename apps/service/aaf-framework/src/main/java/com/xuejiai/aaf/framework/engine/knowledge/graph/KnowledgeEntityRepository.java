package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

/** 知识图谱实体仓储 */
public interface KnowledgeEntityRepository extends Neo4jRepository<KnowledgeEntity, String> {

    List<KnowledgeEntity> findByKnowledgeBaseId(Long knowledgeBaseId);

    List<KnowledgeEntity> findByNameContaining(String keyword);

    Optional<KnowledgeEntity> findByNameAndKnowledgeBaseId(String name, Long knowledgeBaseId);

    /** 查询 N 跳邻居节点 */
    @Query("MATCH (n)-[*1..$hops]-(m) WHERE n.id = $entityId RETURN DISTINCT m")
    List<KnowledgeEntity> findNeighbors(String entityId, int hops);
}
