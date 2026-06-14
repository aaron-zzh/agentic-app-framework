package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.*;

import org.neo4j.driver.Driver;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 图增强检索服务 — 子图搜索、路径发现、社区检测 */
@Service
@RequiredArgsConstructor
public class GraphSearchService {

    private final Driver driver;

    /** N 跳子图检索 */
    public List<KnowledgeEntity> subgraphSearch(String entityId, int hops) {
        var cypher =
                "MATCH (n)-[*1..%d]-(m) WHERE n.id = $entityId RETURN DISTINCT m".formatted(hops);
        try (var session = driver.session()) {
            return session.run(cypher, Map.of("entityId", entityId))
                    .list(record -> mapToEntity(record.get("m").asNode()));
        }
    }

    /** 两实体间所有最短路径 */
    public List<List<KnowledgeEntity>> findPaths(String fromId, String toId, int maxDepth) {
        var cypher =
                "MATCH p = allShortestPaths((a)-[*..%d]-(b)) WHERE a.id = $fromId AND b.id = $toId RETURN p"
                        .formatted(maxDepth);
        try (var session = driver.session()) {
            return session.run(cypher, Map.of("fromId", fromId, "toId", toId))
                    .list(
                            record -> {
                                Path path = record.get("p").asPath();
                                List<KnowledgeEntity> entities = new ArrayList<>();
                                path.nodes().forEach(node -> entities.add(mapToEntity(node)));
                                return entities;
                            });
        }
    }

    /** 简化版社区发现 — 按连通分量分组 */
    public Map<Integer, List<KnowledgeEntity>> detectCommunities(Long knowledgeBaseId) {
        var cypher =
                """
                MATCH (n:KnowledgeEntity {knowledgeBaseId: $kbId})
                OPTIONAL MATCH (n)--(m:KnowledgeEntity {knowledgeBaseId: $kbId})
                RETURN n.id AS nodeId, n AS node, collect(m.id) AS neighbors
                """;
        try (var session = driver.session()) {
            var records = session.run(cypher, Map.of("kbId", knowledgeBaseId)).list();

            Map<String, List<String>> adjacency = new HashMap<>();
            Map<String, KnowledgeEntity> nodeMap = new HashMap<>();
            for (var record : records) {
                String nodeId = record.get("nodeId").asString();
                nodeMap.put(nodeId, mapToEntity(record.get("node").asNode()));
                adjacency.put(nodeId, record.get("neighbors").asList(v -> v.asString()));
            }

            Set<String> visited = new HashSet<>();
            Map<Integer, List<KnowledgeEntity>> communities = new HashMap<>();
            int communityId = 0;
            for (String nodeId : adjacency.keySet()) {
                if (visited.contains(nodeId)) continue;
                List<KnowledgeEntity> community = new ArrayList<>();
                Queue<String> queue = new LinkedList<>();
                queue.add(nodeId);
                visited.add(nodeId);
                while (!queue.isEmpty()) {
                    String current = queue.poll();
                    community.add(nodeMap.get(current));
                    for (String neighbor : adjacency.getOrDefault(current, List.of())) {
                        if (neighbor != null && visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
                communities.put(communityId++, community);
            }
            return communities;
        }
    }

    private KnowledgeEntity mapToEntity(Node node) {
        var entity = new KnowledgeEntity();
        entity.setId(node.get("id").asString(null));
        entity.setName(node.get("name").asString(null));
        entity.setType(node.get("type").asString(null));
        entity.setKnowledgeBaseId(node.get("knowledgeBaseId").asLong(0));
        return entity;
    }
}
