package com.xuejiai.aaf.framework.security;

import org.springframework.context.ApplicationContext;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.spring.ApplicationContextHolder;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * 实体监听器——自动填充 operatorType 和 ownerId。
 *
 * <p>通过 {@code orm.xml} 全局注册，与 Spring Data JPA 的 {@code @CreatedBy/@LastModifiedBy} 互补。
 * JPA 审计填充 createBy/updateBy（Long），本监听器填充 createByType/updateByType/ownerId。
 */
public class OperatorEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        if (!(entity instanceof BaseEntity base)) return;
        OperatorContext ctx = getOperatorContext();
        if (ctx == null) return;
        base.setCreateByType(ctx.currentOperatorType().name());
        if (base.getOwnerId() == null) {
            base.setOwnerId(ctx.currentOwnerId().orElse(base.getCreateBy()));
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
