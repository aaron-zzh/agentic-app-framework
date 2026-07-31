package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

/** 知识图谱实体仓储 */
public interface KnowledgeEntityRepository extends Neo4jRepository<KnowledgeEntity, String> {

    List<KnowledgeEntity> findByKnowledgeBaseId(Long knowledgeBaseId);

    @Query(
            """
            MATCH (source:KnowledgeEntity)
            WHERE source.knowledgeBaseId = $knowledgeBaseId
            OPTIONAL MATCH (source)-[relation:RELATES_TO]->(target:KnowledgeEntity)
            WHERE target.knowledgeBaseId = $knowledgeBaseId
            RETURN source, collect(relation), collect(target)
            """)
    List<KnowledgeEntity> findGraphByKnowledgeBaseId(Long knowledgeBaseId);

    @Query(
            """
            MATCH (source:KnowledgeEntity)-[relation:RELATES_TO]->(target:KnowledgeEntity)
            WHERE source.knowledgeBaseId = $knowledgeBaseId
              AND target.knowledgeBaseId = $knowledgeBaseId
              AND relation.sourceDocumentId = $documentId
            DELETE relation
            """)
    void deleteDocumentRelations(Long knowledgeBaseId, Long documentId);

    @Query(
            """
            MATCH (entity:KnowledgeEntity)
            WHERE entity.knowledgeBaseId = $knowledgeBaseId
              AND entity.sourceDocumentId IS NOT NULL
              AND NOT (entity)--()
            DELETE entity
            """)
    void deleteExtractedOrphanEntities(Long knowledgeBaseId);

    List<KnowledgeEntity> findByNameContaining(String keyword);

    Optional<KnowledgeEntity> findByNameAndKnowledgeBaseId(String name, Long knowledgeBaseId);

    /** 查询 N 跳邻居节点 */
    @Query("MATCH (n)-[*1..$hops]-(m) WHERE n.id = $entityId RETURN DISTINCT m")
    List<KnowledgeEntity> findNeighbors(String entityId, int hops);
}
