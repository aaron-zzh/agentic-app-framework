package com.xuejiai.aaf.module.system.org.api;

import java.util.Objects;

/** 系统组织模块对其他业务模块暴露的可信默认上下文接口。 */
public interface SystemOrgContextApi {

    /** 解析个人默认组织与工作区。 */
    DefaultContext resolvePersonalDefault(Long userId);

    /** 已解析的默认组织上下文。 */
    record DefaultContext(Long orgId, Long workspaceId) {

        public DefaultContext {
            Objects.requireNonNull(orgId, "orgId 不能为空");
            Objects.requireNonNull(workspaceId, "workspaceId 不能为空");
        }
    }
}
