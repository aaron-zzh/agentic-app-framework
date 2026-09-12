package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;

/** Assistant durable runtime facts 的公共持久化边界。 */
@MappedSuperclass
public abstract class AssistantRuntimeEntity extends BaseEntity {

    /** 将领域 TenantId 的组织字符串投影还原为 BaseEntity 组织身份。 */
    @Transient
    public final String getTenantId() {
        return getOrgId() == null ? null : getOrgId().toString();
    }

    /** 将领域 TenantId 的组织字符串投影写入唯一 org_id 租户事实。 */
    public final void setTenantId(String tenantId) {
        setOrgId(tenantId == null ? null : Long.valueOf(tenantId));
    }
}
