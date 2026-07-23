package com.xuejiai.aaf.framework.crud.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

import com.xuejiai.aaf.framework.crud.definition.FieldCapability;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationEffect;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationRequest;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationTarget;
import com.xuejiai.aaf.framework.security.authorization.FieldAccessSupport;
import com.xuejiai.aaf.framework.security.authorization.RecordRuleSupport;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class CrudDataAuthorizationProviderTest extends BaseMockitoUnitTest {

    @Mock private ObjectProvider<RecordRuleSupport> recordRuleSupportProvider;
    @Mock private ObjectProvider<FieldAccessSupport> fieldAccessSupportProvider;
    @Mock private RecordRuleSupport recordRuleSupport;
    @Mock private FieldAccessSupport fieldAccessSupport;

    private CrudDataAuthorizationProvider provider;

    @BeforeEach
    void setUp() {
        OrgContext.setCurrentOrgId(31L);
        OrgContext.setCurrentWorkspaceId(41L);
        provider =
                new CrudDataAuthorizationProvider(
                        recordRuleSupportProvider, fieldAccessSupportProvider);
    }

    @AfterEach
    void tearDown() {
        OrgContext.clear();
    }

    @Test
    @DisplayName("Given 有效租户工作区与 L3 SPI When PDP 求值 Then 返回 SQL scope 字段拒绝和访问版本约束")
    void should_return_typed_constraint_when_context_and_supports_are_valid() {
        // 准备参数
        when(recordRuleSupportProvider.getIfAvailable()).thenReturn(recordRuleSupport);
        when(fieldAccessSupportProvider.getIfAvailable()).thenReturn(fieldAccessSupport);
        when(recordRuleSupport.<Object>compile("system.todo", 7L))
                .thenReturn(RecordRule.allowAll("rule-3"));
        when(fieldAccessSupport.deniedFields("system.todo", 7L))
                .thenReturn(Map.of(FieldCapability.WRITE, Set.of("title")));

        // 调用
        var result = provider.evaluate(request(), requirement());

        // 断言
        assertThat(result.effect()).isEqualTo(AuthorizationEffect.ALLOW);
        assertThat(result.constraint())
                .isInstanceOfSatisfying(
                        CrudDataAuthorizationConstraint.class,
                        constraint -> {
                            assertThat(constraint.resourceKey()).isEqualTo("system.todo");
                            assertThat(constraint.recordScope()).isNotNull();
                            assertThat(constraint.deniedFields().get(FieldCapability.WRITE))
                                    .containsExactly("title");
                            assertThat(constraint.accessVersion()).isEqualTo("rule-3");
                        });
    }

    @Test
    @DisplayName("Given 请求主体与当前工作区不一致 When PDP 求值 Then 不调用 SPI 并返回不确定")
    void should_be_indeterminate_when_workspace_context_mismatches() {
        // 准备参数
        OrgContext.setCurrentWorkspaceId(99L);

        // 调用
        var result = provider.evaluate(request(), requirement());

        // 断言
        assertThat(result.effect()).isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(result.constraint()).isNull();
        verifyNoInteractions(recordRuleSupportProvider, fieldAccessSupportProvider);
    }

    @Test
    @DisplayName("Given 任一 L3 SPI 缺失或异常 When PDP 求值 Then 返回不确定且不泄漏部分约束")
    void should_be_indeterminate_when_support_is_missing_or_fails() {
        // 准备参数
        when(recordRuleSupportProvider.getIfAvailable()).thenReturn(recordRuleSupport);
        when(fieldAccessSupportProvider.getIfAvailable())
                .thenReturn(null)
                .thenReturn(fieldAccessSupport);
        when(recordRuleSupport.<Object>compile("system.todo", 7L))
                .thenThrow(new IllegalStateException("broken"));

        // 调用
        var missing = provider.evaluate(request(), requirement());
        var failed = provider.evaluate(request(), requirement());

        // 断言
        assertThat(missing.effect()).isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(failed.effect()).isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(missing.constraint()).isNull();
        assertThat(failed.constraint()).isNull();
    }

    private AuthorizationRequest request() {
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        null,
                        AuthorizationPlan.DataPlan.all(requirement()),
                        null);
        return new AuthorizationRequest(
                new AuthorizationSubject(7L, 7L, 31L, 41L),
                new AuthorizationTarget("system.todo", "read", null),
                plan,
                Map.of(),
                Duration.ofMinutes(10));
    }

    private AuthorizationPlan.DataRequirement requirement() {
        return CrudDataAuthorizationProvider.requirement(
                TenantScope.WORKSPACE_REQUIRED, AccessMode.DEFAULT);
    }
}
