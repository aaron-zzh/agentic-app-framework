package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Service;

/**
 * 图谱维护服务 — 实体合并、健康检查、文档清理
 */
@Service
@RequiredArgsConstructor
public class GraphMaintenanceService {

    private final Driver driver;

    /** 合并实体：将 source 的关系转移到 target，然后删除 source */
    public void mergeEntity(Long sourceId, Long targetId) {
        var cypher = """
                MATCH (s) WHERE id(s) = $sourceId
                MATCH (t) WHERE id(t) = $targetId
                MATCH (s)-[r]-(x) WHERE x <> t
                WITH t, x, type(r) AS relType, properties(r) AS props
                CREATE (t)-[newR:RELATES_TO]->(x) SET newR = props
                WITH t
                MATCH (s) WHERE id(s) = $sourceId DETACH DELETE s
                """;
        try (var session = driver.session()) {
            session.run(cypher, Map.of("sourceId", sourceId, "targetId", targetId));
        }
    }

    /** 图谱健康度检查 */
    public GraphHealthReport healthCheck(Long knowledgeBaseId) {
        var cypher = """
                MATCH (n:KnowledgeEntity {knowledgeBaseId: $kbId})
                WITH count(n) AS totalNodes
                OPTIONAL MATCH (:KnowledgeEntity {knowledgeBaseId: $kbId})-[r]-(:KnowledgeEntity {knowledgeBaseId: $kbId})
                WITH totalNodes, count(r) AS totalRelations
                OPTIONAL MATCH (iso:KnowledgeEntity {knowledgeBaseId: $kbId}) WHERE NOT (iso)--()
                RETURN totalNodes, totalRelations, count(iso) AS isolatedNodes,
                       CASE WHEN totalNodes > 0 THEN toFloat(totalRelations * 2) / totalNodes ELSE 0.0 END AS avgDegree
                """;
        try (var session = driver.session()) {
            var record = session.run(cypher, Map.of("kbId", knowledgeBaseId)).single();
            return new GraphHealthReport(
                    record.get("totalNodes").asLong(),
                    record.get("totalRelations").asLong(),
                    record.get("isolatedNodes").asLong(),
                    record.get("avgDegree").asDouble()
            );
        }
    }

    /** 删除某文档抽取的所有实体和关系 */
    public void removeByDocument(Long documentId) {
        var cypher = "MATCH (n:KnowledgeEntity {documentId: $documentId}) DETACH DELETE n";
        try (var session = driver.session()) {
            session.run(cypher, Map.of("documentId", documentId));
        }
    }
}
