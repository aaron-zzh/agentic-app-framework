package com.xuejiai.aaf.module.system.log.event;

/** 实体变更事件。 */
public record EntityChangeEvent(String entityType, Long entityId, String action, String changes) {}
