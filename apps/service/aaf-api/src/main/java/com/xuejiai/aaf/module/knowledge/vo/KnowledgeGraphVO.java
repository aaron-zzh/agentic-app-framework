package com.xuejiai.aaf.module.knowledge.vo;

import java.util.List;

/** 知识库图谱响应。 */
public record KnowledgeGraphVO(List<GraphNodeVO> nodes, List<GraphEdgeVO> edges) {

    /** 图谱节点。 */
    public record GraphNodeVO(
            String id, String label, String type, String description, Long sourceDocumentId) {}

    /** 图谱边。 */
    public record GraphEdgeVO(
            String id,
            String source,
            String target,
            String label,
            Double confidence,
            Long sourceDocumentId) {}
}
