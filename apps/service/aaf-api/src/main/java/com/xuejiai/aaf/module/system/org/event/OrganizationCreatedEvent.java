package com.xuejiai.aaf.module.system.org.event;

/** 组织及其必要成员关系已创建，订阅者可在同一事务内初始化组织级资源。 */
public record OrganizationCreatedEvent(Long organizationId) {}
