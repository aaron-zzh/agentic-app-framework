package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.List;

/** 事实抽取结构化输出：JSON Schema 顶层须为对象，事实列表包装为 {@code facts} 字段。 */
public record ExtractedFacts(List<ExtractedTriple> facts) {}
