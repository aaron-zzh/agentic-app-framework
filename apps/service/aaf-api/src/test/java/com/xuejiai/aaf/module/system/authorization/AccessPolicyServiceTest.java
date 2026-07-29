package com.xuejiai.aaf.module.system.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import com.xuejiai.aaf.framework.security.authorization.AuthorizationPolicy;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationTarget;
import com.xuejiai.aaf.framework.security.authorization.PermissionVersionService;
import com.xuejiai.aaf.framework.security.authorization.PolicyCompilationException;
import com.xuejiai.aaf.framework.security.authorization.PolicyDslCompiler;
import com.xuejiai.aaf.framework.security.authorization.PolicyExpressionEvaluator;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

@DisplayName("AccessPolicyService 单元测试")
class AccessPolicyServiceTest extends BaseMockitoUnitTest {

    @Mock private AccessPolicyRepository repository;
    @Mock private AccessPolicySnapshotRepository snapshotRepository;
    @Mock private PermissionVersionService versionService;
    @Mock private AuthorizationAuditService auditService;

    @Captor private ArgumentCaptor<AccessPolicySnapshot> snapshotCaptor;
    @Captor private ArgumentCaptor<Collection<String>> lifecycleCaptor;

    private AccessPolicyService service;

    @BeforeEach
    void setUp() {
        service =
                new AccessPolicyService(
                        repository,
                        snapshotRepository,
                        versionService,
                        new PolicyDslCompiler(),
                        new PolicyExpressionEvaluator(),
                        auditService);
    }

    @AfterEach
    void cleanTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("Given 新策略 When 创建 Then 保存为 DRAFT 且不生成快照")
    void should_create_draft_without_snapshot() {
        // 准备参数
        var dto =
                new AccessPolicyCreateDTO(
                        "草稿策略", null, "{}", Map.of(), "ALLOW", 20, "document", "read");
        when(repository.save(any(AccessPolicy.class)))
                .thenAnswer(
                        invocation -> {
                            var policy = invocation.getArgument(0, AccessPolicy.class);
                            policy.setId(1L);
                            return policy;
                        });

        // 调用
        var result = service.create(dto);

        // 断言
        assertThat(result.lifecycle()).isEqualTo(AuthorizationPolicy.Lifecycle.DRAFT.name());
        assertThat(result.publishedVersion()).isZero();
        verify(snapshotRepository, never()).save(any());
        verify(versionService, never()).bumpPolicyVersion();
    }

    @Test
    @DisplayName("Given 草稿策略和已缓存快照 When 发布并提交事务 Then 保存不可变版本并失效缓存")
    void should_publish_immutable_snapshot_and_invalidate_cache_after_commit() {
        // 准备参数
        var policy = policy(7L, AuthorizationPolicy.Lifecycle.DRAFT, 2L);
        var target = new AuthorizationTarget("document", "read", null);
        when(versionService.policyVersion()).thenReturn("11");
        when(repository.findByLifecycleInOrderByPriority(anyCollection())).thenReturn(List.of());
        when(repository.findById(7L)).thenReturn(Optional.of(policy));
        when(repository.save(any(AccessPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.save(any(AccessPolicySnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.loadSnapshot(target);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        // 调用
        var result = service.publish(7L, new AccessPolicyPublishDTO("ENFORCE"));

        // 断言
        verify(snapshotRepository).save(snapshotCaptor.capture());
        var persistedSnapshot = snapshotCaptor.getValue();
        assertThat(persistedSnapshot.getPolicyId()).isEqualTo(7L);
        assertThat(persistedSnapshot.getPolicyVersion()).isEqualTo(3L);
        assertThat(persistedSnapshot.getLifecycle())
                .isEqualTo(AuthorizationPolicy.Lifecycle.ENFORCE.name());
        assertThat(persistedSnapshot.getName()).isEqualTo("文档读取策略");
        assertThat(result.publishedVersion()).isEqualTo(3L);
        assertThat(result.lifecycle()).isEqualTo(AuthorizationPolicy.Lifecycle.ENFORCE.name());

        policy.setName("事务内继续编辑草稿");
        assertThat(persistedSnapshot.getName()).isEqualTo("文档读取策略");
        service.loadSnapshot(target);
        verify(repository, times(2)).findByLifecycleInOrderByPriority(anyCollection());
        verify(versionService, never()).bumpPolicyVersion();
        verify(auditService, never()).recordPolicyLifecycle(any(), any(), any(), any());

        TransactionSynchronizationUtils.triggerAfterCommit();

        verify(versionService).bumpPolicyVersion();
        verify(auditService)
                .recordPolicyLifecycle(
                        eq("POLICY_PUBLISHED"), eq(policy), eq("11"), contains("ENFORCE"));
        service.loadSnapshot(target);
        verify(repository, times(3)).findByLifecycleInOrderByPriority(anyCollection());
    }

    @Test
    @DisplayName("Given 活动策略 When 编辑 Then 撤回为草稿并在提交后失效运行时快照")
    void should_draft_active_policy_when_updated() {
        // 准备参数
        var policy = policy(7L, AuthorizationPolicy.Lifecycle.ENFORCE, 3L);
        var dto =
                new AccessPolicyCreateDTO(
                        "已编辑策略", null, "{}", Map.of(), "DENY", 20, "todo", "update");
        when(repository.findById(7L)).thenReturn(Optional.of(policy));
        when(repository.save(any(AccessPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(versionService.policyVersion()).thenReturn("17");

        // 调用
        var result = service.update(7L, dto);

        // 断言
        assertThat(result.lifecycle()).isEqualTo(AuthorizationPolicy.Lifecycle.DRAFT.name());
        assertThat(result.publishedVersion()).isEqualTo(3L);
        assertThat(result.targetResource()).isEqualTo("todo");
        verify(snapshotRepository, never()).save(any());
        verify(versionService).bumpPolicyVersion();
        verify(auditService)
                .recordPolicyLifecycle(
                        eq("POLICY_DRAFTED"), eq(policy), eq("17"), contains("编辑活动策略"));
    }

    @Test
    @DisplayName("Given Redis 或相关策略元数据变化 When 连续加载 Then 生成不同的固定长度摘要")
    void should_change_fixed_length_digest_when_relevant_policy_metadata_changes() {
        // 准备参数
        var firstPolicy = policy(7L, AuthorizationPolicy.Lifecycle.ENFORCE, 3L);
        var replacedPolicy = policy(8L, AuthorizationPolicy.Lifecycle.ENFORCE, 3L);
        var republishedPolicy = policy(8L, AuthorizationPolicy.Lifecycle.ENFORCE, 4L);
        var shadowPolicy = policy(8L, AuthorizationPolicy.Lifecycle.SHADOW, 4L);
        var wildcardPolicy = policy(8L, AuthorizationPolicy.Lifecycle.SHADOW, 4L);
        wildcardPolicy.setTargetResource("*");
        var firstSnapshot = AccessPolicySnapshot.from(firstPolicy, 3L, "ENFORCE");
        var replacedSnapshot = AccessPolicySnapshot.from(replacedPolicy, 3L, "ENFORCE");
        var republishedSnapshot = AccessPolicySnapshot.from(republishedPolicy, 4L, "ENFORCE");
        var shadowSnapshot = AccessPolicySnapshot.from(shadowPolicy, 4L, "SHADOW");
        var wildcardSnapshot = AccessPolicySnapshot.from(wildcardPolicy, 4L, "SHADOW");
        when(versionService.policyVersion())
                .thenReturn("global-1", "global-1", "global-1", "global-1", "global-1", "global-2");
        when(repository.findByLifecycleInOrderByPriority(anyCollection()))
                .thenReturn(
                        List.of(firstPolicy),
                        List.of(replacedPolicy),
                        List.of(republishedPolicy),
                        List.of(shadowPolicy),
                        List.of(wildcardPolicy),
                        List.of(wildcardPolicy));
        when(snapshotRepository.findByPolicyIdInAndPolicyVersionIn(
                        anyCollection(), anyCollection()))
                .thenReturn(
                        List.of(firstSnapshot),
                        List.of(replacedSnapshot),
                        List.of(republishedSnapshot),
                        List.of(shadowSnapshot),
                        List.of(wildcardSnapshot),
                        List.of(wildcardSnapshot));
        var target = new AuthorizationTarget("document", "read", null);

        // 调用
        var snapshots =
                List.of(
                        service.loadSnapshot(target),
                        service.loadSnapshot(target),
                        service.loadSnapshot(target),
                        service.loadSnapshot(target),
                        service.loadSnapshot(target),
                        service.loadSnapshot(target));

        // 断言
        assertThat(snapshots)
                .extracting(AuthorizationPolicy.Snapshot::version)
                .allSatisfy(
                        version -> {
                            assertThat(version).hasSize(64);
                            assertThat(version).matches("[0-9a-f]{64}");
                        })
                .doesNotHaveDuplicates();
        verify(repository, times(6)).findByLifecycleInOrderByPriority(anyCollection());
        verify(snapshotRepository, times(6))
                .findByPolicyIdInAndPolicyVersionIn(anyCollection(), anyCollection());
    }

    @Test
    @DisplayName("Given 相同相关策略但返回顺序变化 When 加载 Then 摘要保持一致并命中缓存")
    void should_sort_relevant_policies_by_id_before_digesting() {
        // 准备参数
        var firstPolicy = policy(7L, AuthorizationPolicy.Lifecycle.ENFORCE, 3L);
        var secondPolicy = policy(8L, AuthorizationPolicy.Lifecycle.SHADOW, 4L);
        var firstSnapshot = AccessPolicySnapshot.from(firstPolicy, 3L, "ENFORCE");
        var secondSnapshot = AccessPolicySnapshot.from(secondPolicy, 4L, "SHADOW");
        when(versionService.policyVersion()).thenReturn("global-1");
        when(repository.findByLifecycleInOrderByPriority(anyCollection()))
                .thenReturn(List.of(secondPolicy, firstPolicy), List.of(firstPolicy, secondPolicy));
        when(snapshotRepository.findByPolicyIdInAndPolicyVersionIn(
                        anyCollection(), anyCollection()))
                .thenReturn(List.of(firstSnapshot, secondSnapshot));
        var target = new AuthorizationTarget("document", "read", null);

        // 调用
        var first = service.loadSnapshot(target);
        var second = service.loadSnapshot(target);

        // 断言
        assertThat(second.version()).isEqualTo(first.version());
        verify(repository, times(2)).findByLifecycleInOrderByPriority(anyCollection());
        verify(snapshotRepository)
                .findByPolicyIdInAndPolicyVersionIn(anyCollection(), anyCollection());
    }

    @Test
    @DisplayName("Given 跨 target 同名事实类型冲突 When 加载当前 target Then 隔离无关策略并保留通配策略")
    void should_isolate_fact_schema_conflicts_across_targets() {
        // 准备参数
        var directPolicy = policy(7L, AuthorizationPolicy.Lifecycle.ENFORCE, 3L);
        directPolicy.setFactSchemaJson("{\"attributes.level\":\"STRING\"}");
        var unrelatedPolicy = policy(8L, AuthorizationPolicy.Lifecycle.ENFORCE, 2L);
        unrelatedPolicy.setTargetResource("workflow");
        unrelatedPolicy.setTargetAction("execute");
        unrelatedPolicy.setFactSchemaJson("{\"attributes.level\":\"NUMBER\"}");
        var wildcardPolicy = policy(9L, AuthorizationPolicy.Lifecycle.SHADOW, 1L);
        wildcardPolicy.setTargetResource("*");
        wildcardPolicy.setTargetAction("*");
        wildcardPolicy.setFactSchemaJson("{\"attributes.level\":\"STRING\"}");
        var directSnapshot = AccessPolicySnapshot.from(directPolicy, 3L, "ENFORCE");
        var wildcardSnapshot = AccessPolicySnapshot.from(wildcardPolicy, 1L, "SHADOW");
        when(versionService.policyVersion()).thenReturn("global-1");
        when(repository.findByLifecycleInOrderByPriority(anyCollection()))
                .thenReturn(List.of(directPolicy, unrelatedPolicy, wildcardPolicy));
        when(snapshotRepository.findByPolicyIdInAndPolicyVersionIn(
                        anyCollection(), anyCollection()))
                .thenReturn(List.of(directSnapshot, wildcardSnapshot));

        // 调用
        var snapshot = service.loadSnapshot(new AuthorizationTarget("document", "read", null));

        // 断言
        assertThat(snapshot.policies()).extracting(AuthorizationPolicy::id).containsExactly(7L, 9L);
        assertThat(snapshot.factSchema().requireType("attributes.level")).hasToString("STRING");
        verify(snapshotRepository)
                .findByPolicyIdInAndPolicyVersionIn(eq(List.of(7L, 9L)), eq(List.of(3L, 1L)));
    }

    @Test
    @DisplayName("Given 同 target 同名事实类型冲突 When 加载 Then fail-closed")
    void should_fail_closed_when_same_target_fact_schema_conflicts() {
        // 准备参数
        var stringPolicy = policy(7L, AuthorizationPolicy.Lifecycle.ENFORCE, 3L);
        stringPolicy.setFactSchemaJson("{\"attributes.level\":\"STRING\"}");
        var numberPolicy = policy(8L, AuthorizationPolicy.Lifecycle.SHADOW, 2L);
        numberPolicy.setFactSchemaJson("{\"attributes.level\":\"NUMBER\"}");
        var stringSnapshot = AccessPolicySnapshot.from(stringPolicy, 3L, "ENFORCE");
        var numberSnapshot = AccessPolicySnapshot.from(numberPolicy, 2L, "SHADOW");
        when(versionService.policyVersion()).thenReturn("global-1");
        when(repository.findByLifecycleInOrderByPriority(anyCollection()))
                .thenReturn(List.of(stringPolicy, numberPolicy));
        when(snapshotRepository.findByPolicyIdInAndPolicyVersionIn(
                        anyCollection(), anyCollection()))
                .thenReturn(List.of(stringSnapshot, numberSnapshot));

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                service.loadSnapshot(
                                        new AuthorizationTarget("document", "read", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("策略事实类型冲突")
                .hasMessageContaining("attributes.level");
    }

    @Test
    @DisplayName("Given 已发布策略 When 撤回或禁用 Then 移出运行时且不生成新快照")
    void should_remove_policy_without_snapshot_when_drafted_or_disabled() {
        // 准备参数
        var drafted = policy(7L, AuthorizationPolicy.Lifecycle.ENFORCE, 3L);
        var disabled = policy(8L, AuthorizationPolicy.Lifecycle.SHADOW, 4L);
        when(repository.findById(7L)).thenReturn(Optional.of(drafted));
        when(repository.findById(8L)).thenReturn(Optional.of(disabled));
        when(repository.save(any(AccessPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(versionService.policyVersion()).thenReturn("12");

        // 调用
        var draftResult = service.toDraft(7L);
        var disabledResult = service.disable(8L);

        // 断言
        assertThat(draftResult.lifecycle()).isEqualTo(AuthorizationPolicy.Lifecycle.DRAFT.name());
        assertThat(disabledResult.lifecycle())
                .isEqualTo(AuthorizationPolicy.Lifecycle.DISABLED.name());
        verify(snapshotRepository, never()).save(any());
        verify(versionService, times(2)).bumpPolicyVersion();
    }

    @Test
    @DisplayName("Given 策略快照加载 When 查询活跃定义 Then 只读取 SHADOW 和 ENFORCE")
    void should_load_only_shadow_and_enforce_policies() {
        // 准备参数
        when(versionService.policyVersion()).thenReturn("13");
        when(repository.findByLifecycleInOrderByPriority(anyCollection())).thenReturn(List.of());

        // 调用
        var snapshot = service.loadSnapshot(new AuthorizationTarget("document", "read", null));

        // 断言
        verify(repository).findByLifecycleInOrderByPriority(lifecycleCaptor.capture());
        assertThat(lifecycleCaptor.getValue())
                .containsExactly(
                        AuthorizationPolicy.Lifecycle.SHADOW.name(),
                        AuthorizationPolicy.Lifecycle.ENFORCE.name());
        assertThat(snapshot.version()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(snapshot.policies()).isEmpty();
    }

    @Test
    @DisplayName("Given 活跃策略缺少发布快照 When 加载 Then fail-closed")
    void should_fail_closed_when_active_snapshot_missing() {
        // 准备参数
        var policy = policy(7L, AuthorizationPolicy.Lifecycle.ENFORCE, 3L);
        when(versionService.policyVersion()).thenReturn("14");
        when(repository.findByLifecycleInOrderByPriority(anyCollection()))
                .thenReturn(List.of(policy));
        when(snapshotRepository.findByPolicyIdInAndPolicyVersionIn(
                        anyCollection(), anyCollection()))
                .thenReturn(List.of());

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                service.loadSnapshot(
                                        new AuthorizationTarget("document", "read", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已发布策略快照缺失");
    }

    @Test
    @DisplayName("Given 活跃策略快照 DSL 损坏 When 加载 Then fail-closed")
    void should_fail_closed_when_snapshot_dsl_corrupted() {
        // 准备参数
        var policy = policy(7L, AuthorizationPolicy.Lifecycle.ENFORCE, 3L);
        policy.setConditionJson("不是合法 JSON");
        var corrupted = AccessPolicySnapshot.from(policy, 3L, "ENFORCE");
        when(versionService.policyVersion()).thenReturn("15");
        when(repository.findByLifecycleInOrderByPriority(anyCollection()))
                .thenReturn(List.of(policy));
        when(snapshotRepository.findByPolicyIdInAndPolicyVersionIn(
                        anyCollection(), anyCollection()))
                .thenReturn(List.of(corrupted));

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                service.loadSnapshot(
                                        new AuthorizationTarget("document", "read", null)))
                .isInstanceOf(PolicyCompilationException.class)
                .hasMessageContaining("策略不是合法 JSON");
    }

    private AccessPolicy policy(Long id, AuthorizationPolicy.Lifecycle lifecycle, Long version) {
        var policy = new AccessPolicy();
        policy.setId(id);
        policy.setName("文档读取策略");
        policy.setDescription("测试策略");
        policy.setConditionJson("{}");
        policy.setFactSchemaJson("{}");
        policy.setEffect(AuthorizationPolicy.PolicyEffect.ALLOW.name());
        policy.setPriority(10);
        policy.setTargetResource("document");
        policy.setTargetAction("read");
        policy.setLifecycle(lifecycle.name());
        policy.setPublishedVersion(version);
        return policy;
    }
}
