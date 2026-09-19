package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 项目文档精确选定版本进入 Assistant 项目上下文的可见策略。 */
public enum AigcProjectDocumentContextMode {
    FULL_TEXT,
    SUMMARY,
    RAG,
    AUTO,
    NONE
}
