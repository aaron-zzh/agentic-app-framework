package com.xuejiai.aaf.framework.crud.definition;

/** 资源的静态租户隔离语义。 */
public enum TenantScope {
    GLOBAL,
    ORG_REQUIRED,
    WORKSPACE_REQUIRED,
    ORG_SHARED_WORKSPACE_OPTIONAL
}
