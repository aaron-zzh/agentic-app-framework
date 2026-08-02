package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

/** 知识库处理管道步骤 */
public enum PipelineStep {
    IMPORT,
    CHUNK,
    STORE,
    EMBED,
    EXTRACT_FACTS,
    PUBLISH
}
