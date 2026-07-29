package com.xuejiai.aaf.framework.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AuthorizationCoreSecurityTest extends BaseMockitoUnitTest {

    @Mock private OperatorContext operatorContext;
    @Mock private ObjectProvider<FunctionPermissionChecker> functionProvider;
    @Mock private ObjectProvider<RelationPermissionChecker> relationProvider;
    @Mock private ObjectProvider<DataAuthorizationProvider> dataProvider;
    @Mock private ObjectProvider<AuthorizationPolicyProvider> policyProvider;
    @Mock private ObjectProvider<AuthorizationChallengeStore> challengeProvider;
    @Mock private ObjectProvider<AuthorizationAuditSink> auditProvider;
    @Mock private FunctionPermissionChecker functionChecker;
    @Mock private RelationPermissionChecker relationChecker;
    @Mock private AuthorizationPolicyProvider policies;
    @Mock private AuthorizationChallengeStore challengeStore;

    private DefaultAuthorizationService service;

    @BeforeEach
    void setUp() {
        when(auditProvider.orderedStream()).thenAnswer(ignored -> Stream.empty());
        when(operatorContext.currentOperatorId()).thenReturn(Optional.of(7L));
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
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
    @DisplayName("Given 公共请求合同 When 检查 record 组件与嵌套类型 Then 不存在 proof 注入入口")
    void should_not_expose_challenge_proof_in_public_request() {
        assertThat(
                        Arrays.stream(AuthorizationRequest.class.getRecordComponents())
                                .map(component -> component.getName()))
                .containsExactly("subject", "target", "plan", "facts", "challengeTtl");
        assertThat(
                        Arrays.stream(AuthorizationRequest.class.getDeclaredClasses())
                                .map(Class::getSimpleName))
                .doesNotContain("ChallengeProof");
    }

    @Test
    @DisplayName("Given L2 ANY_APPLICABLE 一项异常一项允许 When 授权 Then 整层不确定并故障关闭")
    void should_fail_closed_when_l2_any_provider_faults() {
        when(relationProvider.getIfAvailable()).thenReturn(relationChecker);
        when(relationChecker.hasPermission(7L, "todo", "9", "owner"))
                .thenThrow(new IllegalStateException("broken"));
        when(relationChecker.hasPermission(7L, "todo", "9", "viewer")).thenReturn(true);
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        AuthorizationPlan.RelationPlan.any(relation("owner"), relation("viewer")),
                        null,
                        null);

        var decision = service.authorize(request(plan, Map.of("risk", "high")));

        assertThat(decision.layerDecisions().get(1).effect())
                .isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.enforcementEffect()).isEqualTo(AuthorizationEffect.DENY);
        verify(relationChecker).hasPermission(7L, "todo", "9", "viewer");
    }

    @Test
    @DisplayName("Given L1-L4 已声明层的 Provider 获取异常 When 授权 Then 各层均不确定且最终拒绝")
    void should_fail_closed_when_layer_object_provider_throws() {
        when(functionProvider.getIfAvailable()).thenThrow(new IllegalStateException("l1"));
        when(relationProvider.getIfAvailable()).thenThrow(new IllegalStateException("l2"));
        when(dataProvider.getIfAvailable()).thenThrow(new IllegalStateException("l3"));
        when(policyProvider.getIfAvailable()).thenThrow(new IllegalStateException("l4"));

        var l1 =
                service.authorize(
                        request(
                                AuthorizationPlan.functionPermission("todo:read"),
                                Map.of("risk", "high")));
        var l2 =
                service.authorize(
                        request(
                                new AuthorizationPlan(
                                        AuthorizationPlan.FunctionRequirement.authenticated(),
                                        AuthorizationPlan.RelationPlan.all(relation("owner")),
                                        null,
                                        null),
                                Map.of("risk", "high")));
        var l3 =
                service.authorize(
                        request(
                                new AuthorizationPlan(
                                        AuthorizationPlan.FunctionRequirement.authenticated(),
                                        null,
                                        AuthorizationPlan.DataPlan.all(
                                                AuthorizationPlan.DataRequirement.of("record")),
                                        null),
                                Map.of("risk", "high")));
        var l4 = service.authorize(request(policyPlan(), Map.of("risk", "high")));

        assertIndeterminate(l1, 0);
        assertIndeterminate(l2, 1);
        assertIndeterminate(l3, 2);
        assertIndeterminate(l4, 3);
    }

    @Test
    @DisplayName("Given 已批准且完整绑定的 challenge When 以相同请求恢复并重放 Then 首次允许且重放拒绝")
    void should_allow_exact_request_once_and_reject_replay() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        var original = request(policyPlan(), Map.of("risk", "high"));
        var stored = challenge(challengeId, original, 1L, "snapshot-1");
        when(challengeProvider.getIfAvailable()).thenReturn(challengeStore);
        when(challengeStore.findApproved(eq(challengeId), eq(7L), any()))
                .thenReturn(Optional.of(stored), Optional.empty());
        when(challengeStore.consume(eq(challengeId), eq(7L), any())).thenReturn(true);
        when(policyProvider.getIfAvailable()).thenReturn(policies);
        when(policies.loadSnapshot(any()))
                .thenReturn(snapshot("snapshot-1", challengePolicy(51L, 1L)));

        // 调用
        var first = service.resume(challengeId, original);
        var replay = service.resume(challengeId, original);

        // 断言
        assertThat(first.allowed()).isTrue();
        assertThat(first.effect()).isEqualTo(AuthorizationEffect.ALLOW);
        assertThat(replay.allowed()).isFalse();
        assertThat(replay.effect()).isEqualTo(AuthorizationEffect.DENY);
        verify(challengeStore).consume(eq(challengeId), eq(7L), any());
    }

    @Test
    @DisplayName("Given APPROVED challenge When 选择 continuation 请求 Then 仅完整绑定请求匹配且不消费")
    void should_select_only_exact_continuation_binding_without_consuming() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        var original = request(policyPlan(), Map.of("risk", "high"));
        var stored = challenge(challengeId, original, 1L, "snapshot-1");
        var tampered =
                new AuthorizationRequest(
                        original.subject(),
                        original.target(),
                        original.plan(),
                        Map.of("risk", "low"),
                        original.challengeTtl());
        when(challengeProvider.getIfAvailable()).thenReturn(challengeStore);
        when(challengeStore.findApproved(eq(challengeId), eq(7L), any()))
                .thenReturn(Optional.of(stored));

        // 调用 + 断言
        assertThat(service.selectContinuation(challengeId, original))
                .isEqualTo(AuthorizationService.ContinuationSelection.MATCH);
        assertThat(service.selectContinuation(challengeId, tampered))
                .isEqualTo(AuthorizationService.ContinuationSelection.NOT_MATCH);
        verify(challengeStore, never()).consume(any(), any(), any());
    }

    @Test
    @DisplayName("Given continuation Store 异常、记录缺失或绑定不完整 When 选择 continuation Then 返回不确定")
    void should_return_indeterminate_when_continuation_store_is_unavailable() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        var original = request(policyPlan(), Map.of("risk", "high"));
        var incomplete =
                new AuthorizationChallengeStore.Challenge(
                        challengeId,
                        original.subject(),
                        original.target(),
                        original.digest(),
                        null,
                        1L,
                        "snapshot-1",
                        Instant.now().plusSeconds(60));
        when(challengeProvider.getIfAvailable())
                .thenThrow(new IllegalStateException("provider broken"))
                .thenReturn(challengeStore);
        when(challengeStore.findApproved(eq(challengeId), eq(7L), any()))
                .thenThrow(new IllegalStateException("store broken"))
                .thenReturn(Optional.empty(), Optional.of(incomplete));

        // 调用
        var providerFailure = service.selectContinuation(challengeId, original);
        var storeFailure = service.selectContinuation(challengeId, original);
        var missingChallenge = service.selectContinuation(challengeId, original);
        var incompleteBinding = service.selectContinuation(challengeId, original);

        // 断言
        assertThat(List.of(providerFailure, storeFailure, missingChallenge, incompleteBinding))
                .containsOnly(AuthorizationService.ContinuationSelection.INDETERMINATE);
        verify(challengeStore, never()).consume(any(), any(), any());
    }

    @Test
    @DisplayName("Given 未批准 challenge When 恢复 Then 拒绝且不消费")
    void should_reject_unapproved_challenge_without_consuming() {
        var challengeId = UUID.randomUUID();
        when(challengeProvider.getIfAvailable()).thenReturn(challengeStore);
        when(challengeStore.findApproved(eq(challengeId), eq(7L), any()))
                .thenReturn(Optional.empty());

        var decision = service.resume(challengeId, request(policyPlan(), Map.of("risk", "high")));

        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.DENY);
        assertThat(decision.allowed()).isFalse();
        verify(challengeStore, never()).consume(any(), any(), any());
    }

    @Test
    @DisplayName("Given challenge 绑定的主体目标计划或事实被篡改 When 恢复 Then 拒绝且不消费")
    void should_reject_tampered_challenge_binding_without_consuming() {
        var original = request(policyPlan(), Map.of("risk", "high"));
        var stored = challenge(UUID.randomUUID(), original, 1L, "snapshot-1");
        var subjectTampered =
                new AuthorizationRequest(
                        new AuthorizationSubject(7L, 8L, 31L, 41L),
                        original.target(),
                        original.plan(),
                        original.facts(),
                        original.challengeTtl());
        var targetTampered =
                new AuthorizationRequest(
                        original.subject(),
                        new AuthorizationTarget("todo", "read", "10"),
                        original.plan(),
                        original.facts(),
                        original.challengeTtl());
        var planTampered =
                new AuthorizationRequest(
                        original.subject(),
                        original.target(),
                        new AuthorizationPlan(
                                AuthorizationPlan.FunctionRequirement.authenticated(),
                                null,
                                AuthorizationPlan.DataPlan.all(
                                        AuthorizationPlan.DataRequirement.of("record")),
                                new AuthorizationPlan.PolicyPlan()),
                        original.facts(),
                        original.challengeTtl());
        var factsTampered =
                new AuthorizationRequest(
                        original.subject(),
                        original.target(),
                        original.plan(),
                        Map.of("risk", "low"),
                        original.challengeTtl());
        var requests = List.of(subjectTampered, targetTampered, planTampered, factsTampered);
        when(challengeProvider.getIfAvailable()).thenReturn(challengeStore);
        requests.forEach(
                request ->
                        when(challengeStore.findApproved(
                                        eq(stored.id()), eq(request.subject().subjectId()), any()))
                                .thenReturn(Optional.of(stored)));

        var decisions =
                requests.stream().map(request -> service.resume(stored.id(), request)).toList();

        assertThat(decisions)
                .allSatisfy(
                        decision -> {
                            assertThat(decision.effect()).isEqualTo(AuthorizationEffect.DENY);
                            assertThat(decision.allowed()).isFalse();
                        });
        verify(challengeStore, never()).consume(any(), any(), any());
    }

    @Test
    @DisplayName("Given challenge 后策略版本或快照变化 When 恢复 Then 消费旧 challenge 但不得允许")
    void should_not_allow_when_policy_version_or_snapshot_changes() {
        var snapshotChallengeId = UUID.randomUUID();
        var policyChallengeId = UUID.randomUUID();
        var original = request(policyPlan(), Map.of("risk", "high"));
        var snapshotChallenge = challenge(snapshotChallengeId, original, 1L, "snapshot-1");
        var policyChallenge = challenge(policyChallengeId, original, 1L, "snapshot-1");
        var snapshotChangedPolicy = challengePolicy(51L, 1L);
        var versionChangedPolicy = challengePolicy(51L, 2L);
        when(challengeProvider.getIfAvailable()).thenReturn(challengeStore);
        when(challengeStore.findApproved(eq(snapshotChallengeId), eq(7L), any()))
                .thenReturn(Optional.of(snapshotChallenge));
        when(challengeStore.findApproved(eq(policyChallengeId), eq(7L), any()))
                .thenReturn(Optional.of(policyChallenge));
        when(challengeStore.consume(any(), any(), any())).thenReturn(true);
        when(challengeStore.create(any())).thenReturn(UUID.randomUUID());
        when(policyProvider.getIfAvailable()).thenReturn(policies);
        when(policies.loadSnapshot(any()))
                .thenReturn(
                        snapshot("snapshot-2", snapshotChangedPolicy),
                        snapshot("snapshot-1", versionChangedPolicy));

        var snapshotChanged = service.resume(snapshotChallengeId, original);
        var policyChanged = service.resume(policyChallengeId, original);

        assertThat(snapshotChanged.allowed()).isFalse();
        assertThat(snapshotChanged.effect()).isEqualTo(AuthorizationEffect.CHALLENGE);
        assertThat(policyChanged.allowed()).isFalse();
        assertThat(policyChanged.effect()).isEqualTo(AuthorizationEffect.CHALLENGE);
        verify(challengeStore).consume(eq(snapshotChallengeId), eq(7L), any());
        verify(challengeStore).consume(eq(policyChallengeId), eq(7L), any());
    }

    @Test
    @DisplayName("Given challenge Store 获取异常 When 创建批准或恢复 Then 安全失败")
    void should_fail_safely_when_challenge_store_provider_throws() {
        when(policyProvider.getIfAvailable()).thenReturn(policies);
        when(policies.loadSnapshot(any()))
                .thenReturn(snapshot("snapshot-1", challengePolicy(51L, 1L)));
        when(challengeProvider.getIfAvailable()).thenThrow(new IllegalStateException("store"));
        var request = request(policyPlan(), Map.of("risk", "high"));

        var authorized = service.authorize(request);
        var approved = service.approveChallenge(UUID.randomUUID());
        var resumed = service.resume(UUID.randomUUID(), request);

        assertThat(authorized.effect()).isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(approved).isFalse();
        assertThat(resumed.effect()).isEqualTo(AuthorizationEffect.INDETERMINATE);
    }

    @Test
    @DisplayName("Given 嵌套 L3 参数 When 构造后修改原始集合 Then 参数与请求摘要保持不变")
    void should_deep_copy_and_stabilize_data_requirement_parameters() {
        var tags = new ArrayList<>(List.of("owner"));
        var nested = new LinkedHashMap<String, Object>();
        nested.put("score", 1.0D);
        nested.put("tags", tags);
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("scope", nested);
        var requirement = new AuthorizationPlan.DataRequirement("record", parameters);
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        null,
                        AuthorizationPlan.DataPlan.all(requirement),
                        null);
        var request = request(plan, Map.of("risk", "high"));
        var digest = request.digest();

        tags.add("editor");
        nested.put("score", 2);
        parameters.put("extra", true);

        @SuppressWarnings("unchecked")
        var copiedScope = (Map<String, Object>) requirement.parameters().get("scope");
        assertThat(copiedScope.get("score")).isEqualTo(new BigDecimal("1"));
        assertThat(copiedScope.get("tags")).isEqualTo(List.of("owner"));
        assertThat(requirement.parameters()).doesNotContainKey("extra");
        assertThat(request.digest()).isEqualTo(digest);
        assertThatThrownBy(() -> copiedScope.put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Given L3 参数含非法对象空值超深或超长内容 When 构造 Then 立即拒绝")
    void should_reject_invalid_data_requirement_parameters() {
        var withNull = new LinkedHashMap<String, Object>();
        withNull.put("value", null);
        Map<String, Object> tooDeep = Map.of("leaf", "value");
        for (var index = 0; index < 20; index++) {
            tooDeep = Map.of("nested", tooDeep);
        }

        assertThatThrownBy(
                        () ->
                                new AuthorizationPlan.DataRequirement(
                                        "record", Map.of("value", new Object())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthorizationPlan.DataRequirement("record", withNull))
                .isInstanceOf(IllegalArgumentException.class);
        var deeplyNested = tooDeep;
        assertThatThrownBy(() -> new AuthorizationPlan.DataRequirement("record", deeplyNested))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new AuthorizationPlan.DataRequirement(
                                        "record", Map.of("value", "x".repeat(4097))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Given 多种授权效果 When 按安全优先级合并 Then DENY 到 ALLOW 顺序固定")
    void should_apply_complete_effect_precedence() {
        assertThat(
                        AuthorizationEffect.strongest(
                                List.of(
                                        AuthorizationEffect.ALLOW,
                                        AuthorizationEffect.CHALLENGE,
                                        AuthorizationEffect.INDETERMINATE,
                                        AuthorizationEffect.DENY)))
                .isEqualTo(AuthorizationEffect.DENY);
        assertThat(
                        AuthorizationEffect.strongest(
                                List.of(
                                        AuthorizationEffect.ALLOW,
                                        AuthorizationEffect.CHALLENGE,
                                        AuthorizationEffect.INDETERMINATE)))
                .isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(
                        AuthorizationEffect.strongest(
                                List.of(AuthorizationEffect.ALLOW, AuthorizationEffect.CHALLENGE)))
                .isEqualTo(AuthorizationEffect.CHALLENGE);
        assertThat(
                        AuthorizationEffect.strongest(
                                List.of(
                                        AuthorizationEffect.NOT_APPLICABLE,
                                        AuthorizationEffect.ALLOW)))
                .isEqualTo(AuthorizationEffect.ALLOW);
        assertThat(AuthorizationEffect.strongest(List.of(AuthorizationEffect.NOT_APPLICABLE)))
                .isEqualTo(AuthorizationEffect.NOT_APPLICABLE);
    }

    private void assertIndeterminate(AuthorizationDecision decision, int layerIndex) {
        assertThat(decision.effect()).isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(decision.layerDecisions().get(layerIndex).effect())
                .isEqualTo(AuthorizationEffect.INDETERMINATE);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.enforcementEffect()).isEqualTo(AuthorizationEffect.DENY);
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

    private AuthorizationRequest request(AuthorizationPlan plan, Map<String, Object> facts) {
        return new AuthorizationRequest(
                new AuthorizationSubject(7L, 7L, 31L, 41L),
                new AuthorizationTarget("todo", "read", "9"),
                plan,
                facts,
                Duration.ofMinutes(10));
    }

    private AuthorizationChallengeStore.Challenge challenge(
            UUID id, AuthorizationRequest request, long policyVersion, String snapshotVersion) {
        return new AuthorizationChallengeStore.Challenge(
                id,
                request.subject(),
                request.target(),
                request.digest(),
                51L,
                policyVersion,
                snapshotVersion,
                Instant.now().plusSeconds(60));
    }

    private AuthorizationPolicy challengePolicy(Long id, long version) {
        return new AuthorizationPolicy(
                id,
                version,
                "策略-" + id,
                new AuthorizationTarget("todo", "read", null),
                1,
                AuthorizationPolicy.Lifecycle.ENFORCE,
                AuthorizationPolicy.PolicyEffect.CHALLENGE,
                "{}");
    }

    private AuthorizationPolicy.Snapshot snapshot(String version, AuthorizationPolicy... policies) {
        return new AuthorizationPolicy.Snapshot(
                version,
                new PolicyFactSchema(Map.of("attributes.risk", PolicyFactSchema.ValueType.STRING)),
                List.of(policies));
    }
}
