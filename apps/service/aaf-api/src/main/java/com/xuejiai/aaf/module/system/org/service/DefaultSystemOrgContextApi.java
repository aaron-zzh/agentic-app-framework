package com.xuejiai.aaf.module.system.org.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.system.org.api.SystemOrgContextApi;

import lombok.RequiredArgsConstructor;

/** 系统组织默认上下文接口实现。 */
@Service
@RequiredArgsConstructor
public class DefaultSystemOrgContextApi implements SystemOrgContextApi {

    private final OrganizationService organizationService;

    @Override
    public DefaultContext resolvePersonalDefault(Long userId) {
        Objects.requireNonNull(userId, "userId 不能为空");
        var context = OrgContext.runIgnoring(() -> organizationService.getDefaultContext(userId));
        return new DefaultContext(
                Long.valueOf(context.orgId()), Long.valueOf(context.workspaceId()));
    }
}
