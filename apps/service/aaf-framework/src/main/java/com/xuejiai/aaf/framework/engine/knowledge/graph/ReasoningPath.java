package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.List;

/** 多跳推理路径结果 */
public record ReasoningPath(
        List<String> entities, List<String> relations, double confidence, int hops) {}
