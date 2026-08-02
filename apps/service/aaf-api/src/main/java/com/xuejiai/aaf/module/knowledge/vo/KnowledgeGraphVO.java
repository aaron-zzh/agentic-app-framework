package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Neo4j 可重建知识图投影视图。 */
public record KnowledgeGraphVO(List<GraphNodeVO> nodes, List<GraphEdgeVO> edges) {

    public record GraphProjectionStatusVO(
            UUID knowledgeBaseId,
            String projectionKind,
            long baseWatermark,
            long desiredWatermark,
            long appliedWatermark,
            String status,
            String rebuildRequestKey,
            String errorMessage,
            LocalDateTime updatedAt,
            boolean ready) {}

    public record GraphNodeVO(String id, String name, String type, String description) {}

    public record GraphEdgeVO(
            String id,
            String factKey,
            String sourceId,
            String targetId,
            String predicate,
            double confidence,
            List<String> evidenceIds) {}
}
