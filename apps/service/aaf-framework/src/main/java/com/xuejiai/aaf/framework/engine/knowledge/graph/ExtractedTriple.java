package com.xuejiai.aaf.framework.engine.knowledge.graph;

/** LLM 抽取的三元组（主语-谓语-宾语） */
public record ExtractedTriple(String subject, String predicate, String object, double confidence) {}
