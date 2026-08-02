package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.neo4j.driver.Driver;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Neo4j 知识投影只读门面。 */
@Service
@RequiredArgsConstructor
public class GraphService {

    private final Driver driver;

    public GraphSnapshot snapshot(UUID knowledgeBaseId) {
        try (var session = driver.session()) {
            var nodes =
                    session.run(
                                    """
                                    MATCH (entity:KnowledgeEntity {knowledgeBaseId: $knowledgeBaseId})
                                    RETURN entity.id AS id, entity.name AS name, entity.type AS type,
                                           entity.description AS description
                                    ORDER BY id
                                    """,
                                    Map.of("knowledgeBaseId", knowledgeBaseId.toString()))
                            .list(
                                    record ->
                                            new GraphNode(
                                                    record.get("id").asString(),
                                                    record.get("name").asString(""),
                                                    record.get("type").asString(""),
                                                    record.get("description").asString("")));
            var edges =
                    session.run(
                                    """
                                    MATCH (source:KnowledgeEntity {knowledgeBaseId: $knowledgeBaseId})
                                          -[fact:FACT]->
                                          (target:KnowledgeEntity {knowledgeBaseId: $knowledgeBaseId})
                                    RETURN fact.id AS id, fact.factKey AS factKey,
                                           source.id AS sourceId, target.id AS targetId,
                                           fact.predicate AS predicate, fact.confidence AS confidence,
                                           fact.evidenceIds AS evidenceIds
                                    ORDER BY factKey
                                    """,
                                    Map.of("knowledgeBaseId", knowledgeBaseId.toString()))
                            .list(
                                    record ->
                                            new GraphEdge(
                                                    record.get("id").asString(),
                                                    record.get("factKey").asString(),
                                                    record.get("sourceId").asString(),
                                                    record.get("targetId").asString(),
                                                    record.get("predicate").asString(""),
                                                    record.get("confidence").asDouble(0),
                                                    record.get("evidenceIds")
                                                            .asList(value -> value.asString())));
            return new GraphSnapshot(nodes, edges);
        }
    }

    public record GraphSnapshot(List<GraphNode> nodes, List<GraphEdge> edges) {}

    public record GraphNode(String id, String name, String type, String description) {}

    public record GraphEdge(
            String id,
            String factKey,
            String sourceId,
            String targetId,
            String predicate,
            double confidence,
            List<String> evidenceIds) {}
}
