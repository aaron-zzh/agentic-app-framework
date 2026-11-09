package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.neo4j.driver.Driver;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Neo4j 知识投影只读门面。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final Driver driver;

    /**
     * 读取知识库的图投影快照。
     *
     * <p>节点与关系分两条语句读取，期间投影仍可能写入新的实体和关系（入库或重建进行中），关系的端点可能不在已读出的节点中；
     * 只返回两端都在节点集中的关系，保证快照自洽，缺失的关系在投影完成后再次读取时出现。
     */
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
            var nodeIds = nodes.stream().map(GraphNode::id).collect(Collectors.toSet());
            var consistentEdges =
                    edges.stream()
                            .filter(
                                    edge ->
                                            nodeIds.contains(edge.sourceId())
                                                    && nodeIds.contains(edge.targetId()))
                            .toList();
            if (consistentEdges.size() < edges.size()) {
                log.info(
                        "知识图快照读取期间投影仍在写入，暂不返回端点缺失的关系: knowledgeBaseId={}, skipped={}",
                        knowledgeBaseId,
                        edges.size() - consistentEdges.size());
            }
            return new GraphSnapshot(nodes, consistentEdges);
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
