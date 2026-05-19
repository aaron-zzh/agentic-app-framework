package com.xuejiai.aaf.framework.engine.knowledge.graph;

/**
 * 图谱健康度报告
 */
public record GraphHealthReport(
        long totalNodes,
        long totalRelations,
        long isolatedNodes,
        double avgDegree
) {}
