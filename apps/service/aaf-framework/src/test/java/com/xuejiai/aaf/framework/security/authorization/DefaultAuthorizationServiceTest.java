package com.xuejiai.aaf.framework.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class DefaultAuthorizationServiceTest extends BaseMockitoUnitTest {

    @Mock private OperatorContext operatorContext;
    @Mock private ObjectProvider<FunctionPermissionChecker> functionProvider;
    @Mock private ObjectProvider<RelationPermissionChecker> relationProvider;
    @Mock private ObjectProvider<DataAuthorizationProvider> dataProvider;
    @Mock private ObjectProvider<AuthorizationPolicyProvider> policyProvider;
    @Mock private ObjectProvider<AuthorizationChallengeStore> challengeProvider;
    @Mock private ObjectProvider<AuthorizationAuditSink> auditProvider;
    @Mock private FunctionPermissionChecker functionChecker;
    @Mock private RelationPermissionChecker relationChecker;
    @Mock private DataAuthorizationProvider dataChecker;
    @Mock private AuthorizationPolicyProvider policies;
    @Mock private AuthorizationChallengeStore challengeStore;

    private DefaultAuthorizationService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(auditProvider.orderedStream())
                .thenAnswer(ignored -> Stream.empty());
        org.mockito.Mockito.lenient()
                .when(operatorContext.currentOperatorId())
                .thenReturn(Optional.of(7L));
        org.mockito.Mockito.lenient()
                .when(operatorContext.currentOwnerId())
                .thenReturn(Optional.of(7L));
        service =
                new DefaultAuthorizationService(
                        operatorContext,
                        functionProvider,
                        relationProvider,
                        dataProvider,
                        policyProvider,
                        challengeProvider,
                        auditProvider,
                        new PolicyDslCompiler(),
                        new PolicyExpressionEvaluator());
    }

    @Test
    @DisplayName("Given 授权计划未声明 L1 When 构造计划 Then 立即拒绝")
    void should_require_l1_when_constructing_plan() {
        assertThatThrownBy(() -> new AuthorizationPlan(null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("l1");
    }

    @Test
    @DisplayName("Given 多种授权效果 When 合并 Then 按 DENY 到 NOT_APPLICABLE 的安全优先级取最强效果")
    void should_combine_effects_by_security_precedence() {
        assertThat(
                        AuthorizationEffect.strongest(
                                List.of(
                                        AuthorizationEffect.NOT_APPLICABLE,
                                        AuthorizationEffect.ALLOW,
                                        AuthorizationEffect.CHALLENGE,
                                        AuthorizationEffect.INDETERMINATE,
                                        AuthorizationEffect.DENY)))
                .isEqualTo(AuthorizationEffect.DENY);
        assertThat(AuthorizationEffect.strongest(List.of(AuthorizationEffect.NOT_APPLICABLE)))
                .isEqualTo(AuthorizationEffect.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("Given ENFORCE ALLOW 与 DENY 均命中 When 授权 Then 正式效果取 DENY")
    void should_apply_strongest_matching_enforce_effect() {
        when(policyProvider.getIfAvailable()).thenReturn(policies);
        when(policies.loadSnapshot(any()))
                .thenReturn(
                        snapshot(
                                policy(
                                        11L,
                                        AuthorizationPolicy.Lifecycle.ENFORCE,
                                        AuthorizationPolicy.PolicyEffect.ALLOW,
                                        "{}"),
                                policy(
                                        12L,
                                        AuthorizationPolicy.Lifecycle.ENFORCE,
                                        AuthorizationPolicy.PolicyEffect.DENY,
                                        "{}")));

        var decision = service.authorize(request(policyPlan()));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.DENY);
        assertThat(decision.layerDecisions().get(3).effect()).isEqualTo(AuthorizationEffect.DENY);
        assertThat(decision.layerDecisions().get(3).policyIds()).containsExactly(11L, 12L);
        assertThat(decision.shadowDecisions()).isEmpty();
    }

    @Test
    @DisplayName("Given 仅声明 L1 When 授权 Then L2-L4 为 NOT_APPLICABLE")
    void should_return_not_applicable_for_undeclared_optional_layers() {
        var decision = service.authorize(request(AuthorizationPlan.authenticated()));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.ALLOW);
        assertThat(decision.layerDecisions())
                .extracting(AuthorizationDecision.LayerDecision::effect)
                .containsExactly(
                        AuthorizationEffect.ALLOW,
                        AuthorizationEffect.NOT_APPLICABLE,
                        AuthorizationEffect.NOT_APPLICABLE,
                        AuthorizationEffect.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("Given L2 已声明但 Provider 缺失 When 授权 Then INDETERMINATE 且执行拒绝")
    void should_fail_closed_when_declared_l2_provider_is_missing() {
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        AuthorizationPlan.RelationPlan.all(relation("owner")),
                        null,
                        null);

        var decision = service.authorize(request(plan));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.enforcementEffect()).isEqualTo(AuthorizationEffect.DENY);
    }

    @Test
    @DisplayName("Given L2 ALL_APPLICABLE 一项拒绝 When 授权 Then L2 拒绝")
    void should_require_all_l2_requirements_for_all_applicable() {
        when(relationProvider.getIfAvailable()).thenReturn(relationChecker);
        when(relationChecker.hasPermission(7L, "todo", "9", "owner")).thenReturn(true);
        when(relationChecker.hasPermission(7L, "todo", "9", "editor")).thenReturn(false);
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        AuthorizationPlan.RelationPlan.all(relation("owner"), relation("editor")),
                        null,
                        null);

        var decision = service.authorize(request(plan));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.DENY);
        assertThat(decision.layerDecisions().get(1).effect()).isEqualTo(AuthorizationEffect.DENY);
    }

    @Test
    @DisplayName("Given L2 ANY_APPLICABLE 一项拒绝一项允许 When 授权 Then 拒绝优先")
    void should_deny_l2_when_any_requirement_denies() {
        when(relationProvider.getIfAvailable()).thenReturn(relationChecker);
        when(relationChecker.hasPermission(7L, "todo", "9", "owner")).thenReturn(false);
        when(relationChecker.hasPermission(7L, "todo", "9", "viewer")).thenReturn(true);
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        AuthorizationPlan.RelationPlan.any(relation("owner"), relation("viewer")),
                        null,
                        null);

        var decision = service.authorize(request(plan));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.DENY);
        assertThat(decision.layerDecisions().get(1).effect()).isEqualTo(AuthorizationEffect.DENY);
    }

    @ParameterizedTest
    @EnumSource(
            value = AuthorizationEffect.class,
            names = {"DENY", "INDETERMINATE", "CHALLENGE"})
    @DisplayName("Given L3 ANY_APPLICABLE 含更强安全效果与允许 When 授权 Then 允许不得覆盖更强效果")
    void should_preserve_stronger_effect_when_l3_any_requirement_allows(
            AuthorizationEffect strongerEffect) {
        when(dataProvider.getIfAvailable()).thenReturn(dataChecker);
        when(dataChecker.evaluate(any(), any()))
                .thenReturn(dataResult(strongerEffect), dataResult(AuthorizationEffect.ALLOW));
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        null,
                        AuthorizationPlan.DataPlan.any(
                                AuthorizationPlan.DataRequirement.of("guard"),
                                AuthorizationPlan.DataRequirement.of("allow")),
                        null);

        var decision = service.authorize(request(plan));

        assertThat(decision.layerDecisions().get(2).effect()).isEqualTo(strongerEffect);
        assertThat(decision.layerDecisions().get(2).items().get(1).constraint())
                .isInstanceOf(TestConstraint.class);
        assertThat(decision.effect()).isEqualTo(strongerEffect);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    @DisplayName("Given L3 ANY_APPLICABLE 一项异常一项允许 When 授权 Then 整层不确定并故障关闭")
    void should_fail_closed_when_l3_any_provider_faults() {
        when(dataProvider.getIfAvailable()).thenReturn(dataChecker);
        when(dataChecker.evaluate(any(), any()))
                .thenThrow(new IllegalStateException("broken"))
                .thenReturn(dataResult(AuthorizationEffect.ALLOW));
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        null,
                        AuthorizationPlan.DataPlan.any(
                                AuthorizationPlan.DataRequirement.of("tenant"),
                                AuthorizationPlan.DataRequirement.of("record")),
                        null);

        var decision = service.authorize(request(plan));

        assertThat(decision.layerDecisions().get(2).effect())
                .isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.enforcementEffect()).isEqualTo(AuthorizationEffect.DENY);
    }

    @Test
    @DisplayName("Given L2 拒绝且 L3 不确定 When 跨层组合 Then DENY 优先")
    void should_apply_security_precedence_across_layers() {
        when(relationProvider.getIfAvailable()).thenReturn(relationChecker);
        when(relationChecker.hasPermission(any(), any(), any(), any())).thenReturn(false);
        when(dataProvider.getIfAvailable()).thenReturn(dataChecker);
        when(dataChecker.evaluate(any(), any()))
                .thenReturn(dataResult(AuthorizationEffect.INDETERMINATE));
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        AuthorizationPlan.RelationPlan.all(relation("owner")),
                        AuthorizationPlan.DataPlan.all(
                                AuthorizationPlan.DataRequirement.of("record")),
                        null);

        var decision = service.authorize(request(plan));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.DENY);
    }

    @Test
    @DisplayName("Given L4 未声明 When 授权 Then 不访问策略 Provider")
    void should_not_load_policy_provider_when_l4_is_undeclared() {
        service.authorize(request(AuthorizationPlan.authenticated()));

        verify(policyProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("Given L4 已声明但 Provider 缺失 When 授权 Then INDETERMINATE")
    void should_fail_closed_when_declared_l4_provider_is_missing() {
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        null,
                        null,
                        new AuthorizationPlan.PolicyPlan());

        var decision = service.authorize(request(plan));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    @DisplayName("Given SHADOW DENY 命中 When 授权 Then 仅返回 shadowDecisions")
    void should_return_shadow_decision_without_affecting_formal_effect() {
        when(policyProvider.getIfAvailable()).thenReturn(policies);
        when(policies.loadSnapshot(any()))
                .thenReturn(
                        snapshot(
                                policy(
                                        21L,
                                        AuthorizationPolicy.Lifecycle.SHADOW,
                                        AuthorizationPolicy.PolicyEffect.DENY,
                                        "{}")));

        var decision = service.authorize(request(policyPlan()));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.ALLOW);
        assertThat(decision.layerDecisions().get(3).effect())
                .isEqualTo(AuthorizationEffect.NOT_APPLICABLE);
        assertThat(decision.shadowDecisions())
                .singleElement()
                .satisfies(
                        shadow -> {
                            assertThat(shadow.policyId()).isEqualTo(21L);
                            assertThat(shadow.effect()).isEqualTo(AuthorizationEffect.DENY);
                        });
    }

    @Test
    @DisplayName("Given DRAFT 与 DISABLED 策略 When 授权 Then 均不求值且不返回影子结果")
    void should_ignore_draft_and_disabled_policies() {
        when(policyProvider.getIfAvailable()).thenReturn(policies);
        when(policies.loadSnapshot(any()))
                .thenReturn(
                        snapshot(
                                policy(
                                        31L,
                                        AuthorizationPolicy.Lifecycle.DRAFT,
                                        AuthorizationPolicy.PolicyEffect.DENY,
                                        "not-json"),
                                policy(
                                        32L,
                                        AuthorizationPolicy.Lifecycle.DISABLED,
                                        AuthorizationPolicy.PolicyEffect.DENY,
                                        "not-json")));

        var decision = service.authorize(request(policyPlan()));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.ALLOW);
        assertThat(decision.shadowDecisions()).isEmpty();
    }

    @Test
    @DisplayName("Given ENFORCE 策略编译失败 When 授权 Then INDETERMINATE 且故障关闭")
    void should_fail_closed_when_enforced_policy_cannot_compile() {
        when(policyProvider.getIfAvailable()).thenReturn(policies);
        when(policies.loadSnapshot(any()))
                .thenReturn(
                        snapshot(
                                policy(
                                        41L,
                                        AuthorizationPolicy.Lifecycle.ENFORCE,
                                        AuthorizationPolicy.PolicyEffect.ALLOW,
                                        "not-json")));

        var decision = service.authorize(request(policyPlan()));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    @DisplayName("Given challenge 已批准 When 恢复两次 Then 首次重算且第二次拒绝")
    void should_reauthorize_once_and_reject_challenge_replay() {
        var challengeId = UUID.randomUUID();
        var policy =
                policy(
                        51L,
                        AuthorizationPolicy.Lifecycle.ENFORCE,
                        AuthorizationPolicy.PolicyEffect.CHALLENGE,
                        "{}");
        var request = request(policyPlan());
        when(policyProvider.getIfAvailable()).thenReturn(policies);
        when(policies.loadSnapshot(any())).thenReturn(snapshot(policy));
        when(challengeProvider.getIfAvailable()).thenReturn(challengeStore);
        when(challengeStore.create(any())).thenReturn(challengeId);

        var challenged = service.authorize(request);
        var stored =
                new AuthorizationChallengeStore.Challenge(
                        challengeId,
                        request.subject(),
                        request.target(),
                        request.digest(),
                        policy.id(),
                        policy.version(),
                        "snapshot-1",
                        Instant.now().plusSeconds(60));
        when(challengeStore.findApproved(eq(challengeId), eq(7L), any()))
                .thenReturn(Optional.of(stored), Optional.empty());
        when(challengeStore.consume(eq(challengeId), eq(7L), any())).thenReturn(true);

        var resumed = service.resume(challengeId, request);
        var replayed = service.resume(challengeId, request);

        assertThat(challenged.effect()).isEqualTo(AuthorizationEffect.CHALLENGE);
        assertThat(resumed.effect()).isEqualTo(AuthorizationEffect.ALLOW);
        assertThat(replayed.effect()).isEqualTo(AuthorizationEffect.DENY);
        verify(challengeStore).consume(eq(challengeId), eq(7L), any());
    }

    @Test
    @DisplayName("Given 同一快照两条 ENFORCE CHALLENGE When 批准一次并恢复 Then 两条策略同时允许且不循环挑战")
    void should_allow_all_matching_challenges_after_single_resume() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        var first =
                policy(
                        51L,
                        AuthorizationPolicy.Lifecycle.ENFORCE,
                        AuthorizationPolicy.PolicyEffect.CHALLENGE,
                        "{}");
        var second =
                policy(
                        52L,
                        AuthorizationPolicy.Lifecycle.ENFORCE,
                        AuthorizationPolicy.PolicyEffect.CHALLENGE,
                        "{}");
        var request = request(policyPlan());
        when(policyProvider.getIfAvailable()).thenReturn(policies);
        when(policies.loadSnapshot(any())).thenReturn(snapshot(first, second));
        when(challengeProvider.getIfAvailable()).thenReturn(challengeStore);
        when(challengeStore.create(any())).thenReturn(challengeId);
        var challenged = service.authorize(request);
        var stored =
                new AuthorizationChallengeStore.Challenge(
                        challengeId,
                        request.subject(),
                        request.target(),
                        request.digest(),
                        first.id(),
                        first.version(),
                        "snapshot-1",
                        Instant.now().plusSeconds(60));
        when(challengeStore.findApproved(eq(challengeId), eq(7L), any()))
                .thenReturn(Optional.of(stored));
        when(challengeStore.consume(eq(challengeId), eq(7L), any())).thenReturn(true);

        // 调用
        var resumed = service.resume(challengeId, request);

        // 断言
        assertThat(challenged.challengeId()).isEqualTo(challengeId);
        assertThat(resumed.effect()).isEqualTo(AuthorizationEffect.ALLOW);
        assertThat(resumed.layerDecisions().get(3).items())
                .extracting(AuthorizationDecision.ItemDecision::effect)
                .containsExactly(AuthorizationEffect.ALLOW, AuthorizationEffect.ALLOW);
        verify(challengeStore).create(any());
        verify(challengeStore).consume(eq(challengeId), eq(7L), any());
    }

    @Test
    @DisplayName("Given L1 ALL 两项权限均允许 When 授权 Then 在同一个 Layer 中逐项允许")
    void should_allow_l1_all_permissions_in_one_layer() {
        // mock 方法
        when(functionProvider.getIfAvailable()).thenReturn(functionChecker);
        when(functionChecker.isRegistered("todo:update")).thenReturn(true);
        when(functionChecker.isRegistered("todo:access-mode:admin-maintenance")).thenReturn(true);
        when(functionChecker.hasPermission(7L, "todo:update")).thenReturn(true);
        when(functionChecker.hasPermission(7L, "todo:access-mode:admin-maintenance"))
                .thenReturn(true);
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.all(
                                "todo:update", "todo:access-mode:admin-maintenance"),
                        null,
                        null,
                        null);

        // 调用
        var decision = service.authorize(request(plan));

        // 断言
        var layer = decision.layerDecisions().getFirst();
        assertThat(layer.effect()).isEqualTo(AuthorizationEffect.ALLOW);
        assertThat(layer.items())
                .extracting(
                        AuthorizationDecision.ItemDecision::key,
                        AuthorizationDecision.ItemDecision::effect)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "todo:update", AuthorizationEffect.ALLOW),
                        org.assertj.core.groups.Tuple.tuple(
                                "todo:access-mode:admin-maintenance", AuthorizationEffect.ALLOW));
    }

    @Test
    @DisplayName("Given L1 ALL 任一权限拒绝 When 授权 Then 整个 L1 拒绝")
    void should_deny_l1_all_when_any_permission_denies() {
        // mock 方法
        when(functionProvider.getIfAvailable()).thenReturn(functionChecker);
        when(functionChecker.isRegistered(any())).thenReturn(true);
        when(functionChecker.hasPermission(7L, "todo:update")).thenReturn(true);
        when(functionChecker.hasPermission(7L, "todo:access-mode:admin-maintenance"))
                .thenReturn(false);
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.all(
                                "todo:update", "todo:access-mode:admin-maintenance"),
                        null,
                        null,
                        null);

        // 调用
        var decision = service.authorize(request(plan));

        // 断言
        assertThat(decision.layerDecisions().getFirst().effect())
                .isEqualTo(AuthorizationEffect.DENY);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    @DisplayName("Given L1 ALL 任一权限检查异常 When 授权 Then 整个 L1 不确定并故障关闭")
    void should_fail_closed_l1_all_when_any_permission_is_indeterminate() {
        // mock 方法
        when(functionProvider.getIfAvailable()).thenReturn(functionChecker);
        when(functionChecker.isRegistered(any())).thenReturn(true);
        when(functionChecker.hasPermission(7L, "todo:update")).thenReturn(true);
        when(functionChecker.hasPermission(7L, "todo:access-mode:admin-maintenance"))
                .thenThrow(new IllegalStateException("broken"));
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.all(
                                "todo:update", "todo:access-mode:admin-maintenance"),
                        null,
                        null,
                        null);

        // 调用
        var decision = service.authorize(request(plan));

        // 断言
        assertThat(decision.layerDecisions().getFirst().effect())
                .isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.enforcementEffect()).isEqualTo(AuthorizationEffect.DENY);
    }

    @Test
    @DisplayName("Given L1 模式与权限码数量不匹配 When 构造 Then 清晰拒绝非法合同")
    void should_reject_ambiguous_l1_function_requirements() {
        assertThatThrownBy(
                        () ->
                                new AuthorizationPlan.FunctionRequirement(
                                        AuthorizationPlan.FunctionMode.AUTHENTICATED,
                                        List.of("todo:read")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new AuthorizationPlan.FunctionRequirement(
                                        AuthorizationPlan.FunctionMode.PERMISSION,
                                        List.of("todo:read", "todo:update")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new AuthorizationPlan.FunctionRequirement(
                                        AuthorizationPlan.FunctionMode.ALL, List.of("todo:read")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AuthorizationPlan policyPlan() {
        return new AuthorizationPlan(
                AuthorizationPlan.FunctionRequirement.authenticated(),
                null,
                null,
                new AuthorizationPlan.PolicyPlan());
    }

    private AuthorizationPlan.RelationRequirement relation(String permission) {
        return new AuthorizationPlan.RelationRequirement("todo", "9", permission);
    }

    private AuthorizationRequest request(AuthorizationPlan plan) {
        return new AuthorizationRequest(
                new AuthorizationSubject(7L, 7L, 31L, 41L),
                new AuthorizationTarget("todo", "read", "9"),
                plan,
                Map.of("risk", "high"),
                Duration.ofMinutes(10));
    }

    private AuthorizationPolicy policy(
            Long id,
            AuthorizationPolicy.Lifecycle lifecycle,
            AuthorizationPolicy.PolicyEffect effect,
            String conditionJson) {
        return new AuthorizationPolicy(
                id,
                1,
                "策略-" + id,
                new AuthorizationTarget("todo", "read", null),
                1,
                lifecycle,
                effect,
                conditionJson);
    }

    private AuthorizationPolicy.Snapshot snapshot(AuthorizationPolicy... policies) {
        return new AuthorizationPolicy.Snapshot(
                "snapshot-1",
                new PolicyFactSchema(Map.of("attributes.risk", PolicyFactSchema.ValueType.STRING)),
                List.of(policies));
    }

    private DataAuthorizationResult dataResult(AuthorizationEffect effect) {
        return effect == AuthorizationEffect.ALLOW
                ? DataAuthorizationResult.allow(new TestConstraint("test"), "L3 允许")
                : new DataAuthorizationResult(effect, null, "L3 非允许");
    }

    private record TestConstraint(String value) implements AuthorizationConstraint {}
}
