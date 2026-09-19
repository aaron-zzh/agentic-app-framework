package com.xuejiai.aaf.module.document.event;

/** 文档版本事务提交后初始化语义元数据的事件。 */
public record DocumentVersionCreatedEvent(
        Long documentVersionId, Long ownerId, Long orgId, Long workspaceId, String sourceType) {}
