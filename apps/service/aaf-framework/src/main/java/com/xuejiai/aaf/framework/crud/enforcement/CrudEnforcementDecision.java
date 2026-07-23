package com.xuejiai.aaf.framework.crud.enforcement;

import org.springframework.data.jpa.domain.Specification;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;

/** 单次 CRUD 入口授权后固定的主体、租户、记录、个人和字段策略。 */
public record CrudEnforcementDecision<E extends BaseEntity>(
        Long subjectId,
        Long orgId,
        Long workspaceId,
        CrudOperation operation,
        AccessMode accessMode,
        Specification<E> tenantScopeSpecification,
        Specification<E> scopeSpecification,
        CompiledFieldPolicy fieldPolicy,
        String accessVersion) {}
