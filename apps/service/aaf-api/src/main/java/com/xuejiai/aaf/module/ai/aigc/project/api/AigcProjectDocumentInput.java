package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 项目物化时提交的初始化文档槽位值；语义角色、引用类型和上下文策略由 Blueprint 冻结定义。 */
public record AigcProjectDocumentInput(
        String fieldKey, Long documentId, Long documentVersionId, String contextNote) {}
