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

    /**
     * 合并重复实体：把 duplicateIds 的所有出边/入边重建到 keepId 上，再删除 duplicateIds 节点。
     *
     * <p>不依赖 APOC 插件（{@code apoc.refactor.mergeNodes}），用纯 Cypher 手动重建关系， 保证在只装了 Community
     * Edition、未装 APOC 的环境下也能跑。关系属性（type/weight/confidence/ sourceDocumentId）原样复制到新关系上。
     */
    @Query(
            """
            MATCH (keep:KnowledgeEntity {id: $keepId})
            UNWIND $duplicateIds AS dupId
            MATCH (dup:KnowledgeEntity {id: dupId})
            OPTIONAL MATCH (dup)-[outRel:RELATES_TO]->(target)
            WHERE target.id <> $keepId
            FOREACH (ignore IN CASE WHEN outRel IS NOT NULL THEN [1] ELSE [] END |
                MERGE (keep)-[newOut:RELATES_TO]->(target)
                SET newOut.type = outRel.type,
                    newOut.weight = outRel.weight,
                    newOut.confidence = outRel.confidence,
                    newOut.sourceDocumentId = outRel.sourceDocumentId,
                    newOut.createdAt = outRel.createdAt
            )
            WITH keep, dup, $duplicateIds AS duplicateIds
            OPTIONAL MATCH (source)-[inRel:RELATES_TO]->(dup)
            WHERE NOT source.id IN duplicateIds
            FOREACH (ignore IN CASE WHEN inRel IS NOT NULL THEN [1] ELSE [] END |
                MERGE (source)-[newIn:RELATES_TO]->(keep)
                SET newIn.type = inRel.type,
                    newIn.weight = inRel.weight,
                    newIn.confidence = inRel.confidence,
                    newIn.sourceDocumentId = inRel.sourceDocumentId,
                    newIn.createdAt = inRel.createdAt
            )
            DETACH DELETE dup
            """)
    void mergeEntities(String keepId, List<String> duplicateIds);

    /** 查询 N 跳邻居节点 */
    @Query("MATCH (n)-[*1..$hops]-(m) WHERE n.id = $entityId RETURN DISTINCT m")
    List<KnowledgeEntity> findNeighbors(String entityId, int hops);
}
