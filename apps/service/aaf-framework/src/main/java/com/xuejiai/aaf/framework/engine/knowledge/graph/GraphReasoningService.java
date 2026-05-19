package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.types.Path;
import org.springframework.stereotype.Service;

/**
 * 多跳推理服务 — 路径发现 + 置信度衰减
 */
@Service
@RequiredArgsConstructor
public class GraphReasoningService {

    private static final double DECAY_FACTOR = 0.8;

    private final Driver driver;
    private final ConcurrentHashMap<String, List<ReasoningPath>> cache = new ConcurrentHashMap<>();

    /** 多跳推理：查找起止实体间路径并计算置信度 */
    public List<ReasoningPath> reason(Long startEntityId, Long endEntityId, int maxHops) {
        var cacheKey = startEntityId + ":" + endEntityId;
        return cache.computeIfAbsent(cacheKey, k -> doReason(startEntityId, endEntityId, maxHops));
    }

    private List<ReasoningPath> doReason(Long startEntityId, Long endEntityId, int maxHops) {
        var cypher = "MATCH p = allShortestPaths((a)-[*..%d]-(b)) WHERE id(a) = $fromId AND id(b) = $toId RETURN p".formatted(maxHops);
        try (var session = driver.session()) {
            return session.run(cypher, Map.of("fromId", startEntityId, "toId", endEntityId))
                    .list(record -> {
                        Path path = record.get("p").asPath();
                        List<String> entities = new ArrayList<>();
                        List<String> relations = new ArrayList<>();
                        path.nodes().forEach(node -> entities.add(node.get("name").asString("")));
                        path.relationships().forEach(rel -> relations.add(rel.type()));
                        int hops = relations.size();
                        double confidence = Math.pow(DECAY_FACTOR, hops);
                        return new ReasoningPath(entities, relations, confidence, hops);
                    });
        }
    }
}
