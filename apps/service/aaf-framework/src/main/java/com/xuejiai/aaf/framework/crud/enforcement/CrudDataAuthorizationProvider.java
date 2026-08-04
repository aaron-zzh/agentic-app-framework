package com.xuejiai.aaf.framework.crud.enforcement;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationRequest;
import com.xuejiai.aaf.framework.security.authorization.DataAuthorizationProvider;
import com.xuejiai.aaf.framework.security.authorization.DataAuthorizationResult;
import com.xuejiai.aaf.framework.security.authorization.FieldAccessSupport;
import com.xuejiai.aaf.framework.security.authorization.RecordRuleSupport;

/** CRUD 的唯一 L3 Provider；统一编译记录范围、字段拒绝集合与访问版本。 */
@Component
public final class CrudDataAuthorizationProvider implements DataAuthorizationProvider {

    public static final String REQUIREMENT_KEY = "crud";
    private static final String TENANT_SCOPE = "tenantScope";
    private static final String ACCESS_MODE = "accessMode";

    private final ObjectProvider<RecordRuleSupport> recordRuleSupportProvider;
    private final ObjectProvider<FieldAccessSupport> fieldAccessSupportProvider;

    public CrudDataAuthorizationProvider(
            ObjectProvider<RecordRuleSupport> recordRuleSupportProvider,
            ObjectProvider<FieldAccessSupport> fieldAccessSupportProvider) {
        this.recordRuleSupportProvider = recordRuleSupportProvider;
        this.fieldAccessSupportProvider = fieldAccessSupportProvider;
    }

    public static AuthorizationPlan.DataRequirement requirement(
            TenantScope tenantScope, AccessMode accessMode) {
        return new AuthorizationPlan.DataRequirement(
                REQUIREMENT_KEY,
                Map.of(
                        TENANT_SCOPE,
                        Objects.requireNonNull(tenantScope, "tenantScope").name(),
                        ACCESS_MODE,
                        Objects.requireNonNull(accessMode, "accessMode").name()));
    }

    @Override
    public DataAuthorizationResult evaluate(
            AuthorizationRequest request, AuthorizationPlan.DataRequirement requirement) {
        try {
            if (request == null
                    || requirement == null
                    || !REQUIREMENT_KEY.equals(requirement.key())) {
                return DataAuthorizationResult.indeterminate("CRUD L3 要求缺失或无法识别");
            }
            var tenantScope = enumParameter(requirement, TENANT_SCOPE, TenantScope.class);
            var accessMode = enumParameter(requirement, ACCESS_MODE, AccessMode.class);
            if (!validContext(request, tenantScope)) {
                return DataAuthorizationResult.indeterminate("CRUD 租户或工作区上下文无效");
            }

            var resourceKey = request.target().resource();
            var subjectId = request.subject().subjectId();
            if (!request.target().hasPolicyTarget() || subjectId == null) {
                return DataAuthorizationResult.indeterminate("CRUD 资源、动作或主体缺失");
            }

            var recordRuleSupport = recordRuleSupportProvider.getIfAvailable();
            var fieldAccessSupport = fieldAccessSupportProvider.getIfAvailable();
            if (recordRuleSupport == null || fieldAccessSupport == null) {
                return DataAuthorizationResult.indeterminate("CRUD L3 支持不可用");
            }
            RecordRule<?> recordRule = recordRuleSupport.compile(resourceKey, subjectId);
            var deniedFields = fieldAccessSupport.deniedFields(resourceKey, subjectId);
            if (recordRule == null
                    || recordRule.specification() == null
                    || recordRule.accessVersion() == null
                    || recordRule.accessVersion().isBlank()
                    || deniedFields == null) {
                return DataAuthorizationResult.indeterminate("CRUD L3 约束不完整");
            }

            var recordScope =
                    accessMode.bypassesRecordScope()
                            ? unrestrictedScope()
                            : recordRule.specification();
            var constraint =
                    new CrudDataAuthorizationConstraint(
                            resourceKey, recordScope, deniedFields, recordRule.accessVersion());
            return DataAuthorizationResult.allow(constraint, "CRUD L3 数据约束已编译");
        } catch (RuntimeException ex) {
            return DataAuthorizationResult.indeterminate("CRUD L3 数据约束编译失败");
        }
    }

    private boolean validContext(AuthorizationRequest request, TenantScope tenantScope) {
        var subject = request.subject();
        if (subject == null
                || !Objects.equals(subject.tenantId(), OrgContext.getCurrentOrgId())
                || !Objects.equals(subject.workspaceId(), OrgContext.getCurrentWorkspaceId())) {
            return false;
        }
        if (OrgContext.isAllOrganizations()) {
            return subject.tenantId() == null
                    && subject.workspaceId() == null
                    && (OrgContext.isAllOrganizationsUnrestricted()
                            || !OrgContext.getAccessibleOrgIds().isEmpty());
        }
        if (OrgContext.isAllWorkspaces()) {
            return subject.tenantId() != null && subject.workspaceId() == null;
        }
        return switch (tenantScope) {
            case GLOBAL -> true;
            case ORG_REQUIRED, ORG_SHARED_WORKSPACE_OPTIONAL -> subject.tenantId() != null;
            case WORKSPACE_REQUIRED -> subject.tenantId() != null && subject.workspaceId() != null;
        };
    }

    private <E extends Enum<E>> E enumParameter(
            AuthorizationPlan.DataRequirement requirement, String name, Class<E> type) {
        var value = requirement.parameters().get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("CRUD L3 参数缺失: " + name);
        }
        return Enum.valueOf(type, text);
    }

    private Specification<Object> unrestrictedScope() {
        return (root, query, builder) -> null;
    }
}
