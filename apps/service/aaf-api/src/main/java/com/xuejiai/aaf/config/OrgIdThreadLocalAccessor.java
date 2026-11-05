package com.xuejiai.aaf.config;

import com.xuejiai.aaf.framework.org.OrgContext;

import io.micrometer.context.ThreadLocalAccessor;

/**
 * 在 Reactor Context 与 {@link OrgContext} 之间传播组织 ID。
 *
 * <p>通过 {@code META-INF/services/io.micrometer.context.ThreadLocalAccessor} 由 ServiceLoader 注册，配合
 * {@code spring.reactor.context-propagation=auto}，使组织上下文能跨 boundedElastic 等调度线程恢复。
 */
public final class OrgIdThreadLocalAccessor implements ThreadLocalAccessor<Long> {

    @Override
    public Object key() {
        return OrgContext.ORG_ID_CONTEXT_KEY;
    }

    @Override
    public Long getValue() {
        return OrgContext.getCurrentOrgId();
    }

    @Override
    public void setValue(Long value) {
        OrgContext.setCurrentOrgId(value);
    }

    /**
     * Context 中无该键时清空线程值（无参 restore() 默认也走这里）。
     *
     * <p>默认实现会调用 reset() 抛异常，导致 Lettuce 等无组织上下文的 Reactor 链路失败；仅在已有 orgId 时清空，避免误清全组织读取上下文。
     */
    @Override
    public void setValue() {
        if (OrgContext.getCurrentOrgId() != null) {
            OrgContext.setCurrentOrgId(null);
        }
    }

    @Override
    public void restore(Long previousValue) {
        OrgContext.setCurrentOrgId(previousValue);
    }
}
