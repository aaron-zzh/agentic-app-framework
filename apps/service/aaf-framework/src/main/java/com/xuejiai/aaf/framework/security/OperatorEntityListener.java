package com.xuejiai.aaf.framework.security;

import org.springframework.context.ApplicationContext;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.spring.ApplicationContextHolder;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * 实体监听器——自动填充 operatorType、ownerId、orgId、workspaceId。
 *
 * <p>通过 {@code orm.xml} 全局注册，与 Spring Data JPA 的 {@code @CreatedBy/@LastModifiedBy} 互补。 JPA 审计填充
 * createBy/updateBy（Long），本监听器填充 createByType/updateByType/ownerId/orgId/workspaceId。
 *
 * <p>orgId/workspaceId 兜底填充仅在业务层未显式设置时生效（不覆盖已有值），取值分别来自 {@link OrgContext#getCurrentOrgId()}/{@link
 * OrgContext#getCurrentWorkspaceId()}（HTTP 请求场景由 {@code OrgFilter} 从 {@code X-Org-Id}/{@code
 * X-Workspace-Id} 头解析并校验归属后写入）。当前上下文没有 对应值（如后台任务、种子数据初始化，或请求未指定工作区）时保持 null，不做 fail-closed 强制校验——
 * workspaceId 为 null 语义上是"组织级共享，不特定某个工作区"，在任何工作区视角下都默认可见，是合法状态， 不需要回填；orgId 缺失的业务数据会在查询侧被 {@code
 * OrgFilterAspect} 的 orgFilter 静默过滤，属于设计上的 纵深防御，此处不重复拦截。
 */
public class OperatorEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        if (!(entity instanceof BaseEntity base)) return;
        OperatorContext ctx = getOperatorContext();
        if (ctx != null) {
            base.setCreateByType(ctx.currentOperatorType().name());
            if (base.getOwnerId() == null) {
                base.setOwnerId(ctx.currentOwnerId().orElse(base.getCreateBy()));
            }
        }
        if (base.getOrgId() == null) {
            base.setOrgId(OrgContext.getCurrentOrgId());
        }
        if (base.getWorkspaceId() == null) {
            base.setWorkspaceId(OrgContext.getCurrentWorkspaceId());
        }
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        if (!(entity instanceof BaseEntity base)) return;
        OperatorContext ctx = getOperatorContext();
        if (ctx == null) return;
        base.setUpdateByType(ctx.currentOperatorType().name());
    }

    private OperatorContext getOperatorContext() {
        ApplicationContext appCtx = ApplicationContextHolder.getContext();
        if (appCtx == null) return null;
        return appCtx.getBean(OperatorContext.class);
    }
}
