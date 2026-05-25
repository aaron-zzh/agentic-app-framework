package com.xuejiai.aaf.framework.security.access;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 默认服务层权限检查——当前全部放行，后续接入数据权限规则。 */
@Slf4j
@Component
public class DefaultServicePermissionChecker implements ServicePermissionChecker {

    @Override
    public boolean check(Long userId, String resourceType, String resourceId, String action) {
        // Layer 1/2 已处理则跳过
        if (AccessContext.isHandledByHigherLayer(AccessLayer.SERVICE)) {
            return true;
        }
        // TODO: 查询资源归属关系，判断 userId 是否有权操作
        log.debug("Layer3 权限检查（占位放行）: userId={}, {}:{}:{}", userId, resourceType, resourceId, action);
        return true;
    }
}
