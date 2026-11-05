package com.xuejiai.aaf.config;

import com.xuejiai.aaf.framework.org.OrgContext;

import io.micrometer.context.ThreadLocalAccessor;

/**
 * 在 Reactor Context 与 {@link OrgContext} 之间传播工作区 ID。
 *
 * <p>注册方式与 {@link OrgIdThreadLocalAccessor} 相同。
 */
public final class WorkspaceIdThreadLocalAccessor implements ThreadLocalAccessor<Long> {

    @Override
    public Object key() {
        return OrgContext.WORKSPACE_ID_CONTEXT_KEY;
    }

    @Override
    public Long getValue() {
        return OrgContext.getCurrentWorkspaceId();
    }

    @Override
    public void setValue(Long value) {
        OrgContext.setCurrentWorkspaceId(value);
    }

    /** Context 中无该键时清空线程值；仅在已有 workspaceId 时清空，避免误清全工作区读取上下文。 */
    @Override
    public void setValue() {
        if (OrgContext.getCurrentWorkspaceId() != null) {
            OrgContext.setCurrentWorkspaceId(null);
        }
    }

    @Override
    public void restore(Long previousValue) {
        OrgContext.setCurrentWorkspaceId(previousValue);
    }
}
