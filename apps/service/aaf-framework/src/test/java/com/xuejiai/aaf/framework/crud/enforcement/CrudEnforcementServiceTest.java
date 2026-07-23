package com.xuejiai.aaf.framework.crud.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.CrudAction;
import com.xuejiai.aaf.framework.crud.definition.CrudCapabilityDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.FieldCapability;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationChallengeRequiredException;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationDecision;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationEffect;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationLayer;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationRequest;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class CrudEnforcementServiceTest extends BaseMockitoUnitTest {

    @Mock private OperatorContext operatorContext;
    @Mock private AuthorizationService authorizationService;
    @Mock private CrudResourceCatalogEntry entry;
    @Mock private CrudResourceDefinition<?> definition;
    @Mock private CrudCapabilityDefinition capabilities;

    private CrudEnforcementService enforcementService;

    @BeforeEach
    void setUp() {
        enforcementService = new CrudEnforcementService(operatorContext, authorizationService);
        prepareDefinition();
    }

    @Test
    @DisplayName("Given 单对象 GET When 执行 preflight Then 仅一次 PDP 且只声明 L1/L3")
    void should_skip_l4_during_object_preflight() {
        // mock 方法
        when(authorizationService.authorize(any()))
                .thenReturn(allowedCrudDecision(Map.of(FieldCapability.READ, Set.of("title"))));

        // 调用
        CrudEnforcementDecision<TestEntity> decision =
                enforcementService.enforceObjectPreflight(
                        entry, CrudOperation.GET, AccessMode.DEFAULT);

        // 断言
        assertThat(decision.accessVersion()).isEqualTo("rule-version");
        assertThat(decision.fieldPolicy().allows("title", FieldCapability.READ)).isFalse();
        var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService, times(1)).authorize(captor.capture());
        var request = captor.getValue();
        assertThat(request.plan().l1().permissionCodes()).containsExactly("system:todo:read");
        assertThat(request.plan().l3()).isNotNull();
        assertThat(request.plan().l4()).isNull();
        assertThat(request.target().objectId()).isNull();
    }

    @Test
    @DisplayName("Given 批量读取 When 执行 request preflight Then 一次 PDP 声明请求级 L4")
    void should_keep_request_level_l4_for_batch_operation() {
        // mock 方法
        when(authorizationService.authorize(any())).thenReturn(allowedCrudDecision(Map.of()));

        // 调用
        enforcementService.enforceRequest(entry, CrudOperation.BATCH_READ, AccessMode.DEFAULT);

        // 断言
        var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService).authorize(captor.capture());
        assertThat(captor.getValue().plan().l4()).isNotNull();
        assertThat(captor.getValue().target().objectId()).isNull();
        assertThat(captor.getValue().facts()).doesNotContainKey(CrudEnforcementService.FACT_NAMESPACE);
    }

    @Test
    @DisplayName("Given 服务端实体和关系要求 When 评估 CURRENT Then objectId、L2 与 CURRENT 绑定同一 target PDP")
    void should_bind_current_entity_and_relation_in_one_target_request() {
        // 准备参数
        var entity = entity(99L, 7L);
        var relation = new AuthorizationPlan.RelationRequirement("todo", "99", "can_read");
        when(authorizationService.authorize(any())).thenReturn(plainAllowedDecision());

        // 调用
        var allowed =
                enforcementService.allowsCurrentTarget(
                        entry,
                        enforcementDecision(CrudOperation.GET),
                        entity,
                        Map.of("id", 99L, "ownerId", 7L),
                        relation);

        // 断言
        assertThat(allowed).isTrue();
        var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService, times(1)).authorize(captor.capture());
        var request = captor.getValue();
        assertThat(request.target().objectId()).isEqualTo("99");
        assertThat(request.plan().l1().mode())
                .isEqualTo(AuthorizationPlan.FunctionMode.AUTHENTICATED);
        assertThat(request.plan().l2().requirements()).containsExactly(relation);
        assertThat(request.plan().l3()).isNull();
        assertThat(request.plan().l4()).isNotNull();
        assertThat(crudFacts(request).get(CrudEnforcementService.FACT_CURRENT))
                .isEqualTo(Map.of("id", new java.math.BigDecimal("99"), "ownerId", new java.math.BigDecimal("7")));
    }

    @Test
    @DisplayName("Given UPDATE 原 DTO 摘要和服务端前后快照 When 评估 target Then 同时绑定 CURRENT、PROPOSED 与原摘要")
    void should_bind_current_proposed_and_request_payload_digest_for_update() {
        // 准备参数
        when(authorizationService.authorize(any())).thenReturn(plainAllowedDecision());
        var payloadDigest = "a".repeat(64);

        // 调用
        enforcementService.requireUpdatedTarget(
                entry,
                enforcementDecision(CrudOperation.UPDATE),
                "99",
                Map.of("id", 99L, "status", "OPEN"),
                Map.of("id", 99L, "status", "DONE", "ownerId", 7L),
                payloadDigest);

        // 断言
        var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService).authorize(captor.capture());
        var request = captor.getValue();
        var facts = crudFacts(request);
        assertThat(facts)
                .containsEntry(CrudEnforcementService.FACT_PAYLOAD_DIGEST, payloadDigest)
                .containsKeys(
                        CrudEnforcementService.FACT_CURRENT,
                        CrudEnforcementService.FACT_PROPOSED)
                .doesNotContainKeys("body", "objectId");
        assertThat(request.target().objectId()).isEqualTo("99");
    }

    @Test
    @DisplayName("Given CREATE 已应用租户和 owner When 评估 target Then 保存前仅携带 CREATED 与 payloadDigest")
    void should_bind_created_server_snapshot_before_save() {
        // 准备参数
        when(authorizationService.authorize(any())).thenReturn(plainAllowedDecision());

        // 调用
        var payloadDigest = "b".repeat(64);
        enforcementService.requireCreatedTarget(
                entry,
                enforcementDecision(CrudOperation.CREATE),
                new TestEntity(),
                Map.of("orgId", 3L, "ownerId", 7L, "status", "DRAFT"),
                payloadDigest);

        // 断言
        var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService).authorize(captor.capture());
        var request = captor.getValue();
        var facts = crudFacts(request);
        assertThat(request.target().objectId()).isNull();
        assertThat(facts)
                .containsEntry(CrudEnforcementService.FACT_PAYLOAD_DIGEST, payloadDigest)
                .containsKey(CrudEnforcementService.FACT_CREATED)
                .doesNotContainKeys(
                        CrudEnforcementService.FACT_CURRENT,
                        CrudEnforcementService.FACT_PROPOSED,
                        "body");
    }

    @Test
    @DisplayName("Given Hook 返回实体对象 When 组装 target facts Then 深不可变校验故障关闭且不调用 PDP")
    void should_fail_closed_when_authorization_attributes_are_not_pure_data() {
        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                enforcementService.requireCreatedTarget(
                                        entry,
                                        enforcementDecision(CrudOperation.CREATE),
                                        new TestEntity(),
                                        Map.of("entity", new TestEntity()),
                                        "c".repeat(64)))
                .isInstanceOf(com.xuejiai.aaf.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        verify(authorizationService, never()).authorize(any());
    }

    @Test
    @DisplayName("Given target L4 返回 challenge When 对象 CRUD 执行 Then 保留 challengeId")
    void should_preserve_challenge_id_for_target_enforcement() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        when(authorizationService.authorize(any()))
                .thenReturn(
                        new AuthorizationDecision(
                                AuthorizationEffect.CHALLENGE,
                                List.of(),
                                List.of(),
                                challengeId,
                                "policy-version"));

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                enforcementService.requireCreatedTarget(
                                        entry,
                                        enforcementDecision(CrudOperation.CREATE),
                                        new TestEntity(),
                                        Map.of("ownerId", 7L),
                                        "d".repeat(64)))
                .isInstanceOf(AuthorizationChallengeRequiredException.class)
                .extracting("challengeId")
                .isEqualTo(challengeId);
    }

    private void prepareDefinition() {
        when(entry.definition()).thenReturn(definition);
        when(entry.fieldPolicy())
                .thenReturn(
                        new CompiledFieldPolicy(
                                Map.of("title", EnumSet.allOf(FieldCapability.class))));
        when(definition.capabilities()).thenReturn(capabilities);
        when(capabilities.operations()).thenReturn(Set.of(CrudOperation.values()));
        when(definition.tenantScope()).thenReturn(TenantScope.GLOBAL);
        when(definition.personalScope()).thenReturn(PersonalScope.byProperty("assigneeId"));
        when(definition.permissionCode(CrudAction.READ)).thenReturn("system:todo:read");
        when(definition.permissionCode(CrudAction.CREATE)).thenReturn("system:todo:create");
        when(definition.permissionCode(CrudAction.UPDATE)).thenReturn("system:todo:update");
        when(definition.accessModePermissionCode(AccessMode.ADMIN_MAINTENANCE))
                .thenReturn("system:todo:access-mode:admin-maintenance");
        when(definition.entitySlug()).thenReturn("system.todo");
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(operatorContext.currentOperatorId()).thenReturn(Optional.of(7L));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> crudFacts(AuthorizationRequest request) {
        return (Map<String, Object>) request.facts().get(CrudEnforcementService.FACT_NAMESPACE);
    }

    private TestEntity entity(Long id, Long ownerId) {
        var entity = new TestEntity();
        entity.setId(id);
        entity.setOwnerId(ownerId);
        return entity;
    }

    private AuthorizationDecision allowedCrudDecision(
            Map<FieldCapability, Set<String>> deniedFields) {
        var constraint =
                new CrudDataAuthorizationConstraint(
                        "system.todo",
                        RecordRule.<TestEntity>allowAll("rule-version").specification(),
                        deniedFields,
                        "rule-version");
        var item =
                new AuthorizationDecision.ItemDecision(
                        CrudDataAuthorizationProvider.REQUIREMENT_KEY,
                        AuthorizationEffect.ALLOW,
                        "CRUD L3 数据约束已编译",
                        constraint);
        var layer =
                new AuthorizationDecision.LayerDecision(
                        AuthorizationLayer.L3_DATA,
                        AuthorizationEffect.ALLOW,
                        "L3 数据约束组合",
                        List.of(item),
                        List.of());
        return new AuthorizationDecision(
                AuthorizationEffect.ALLOW, List.of(layer), List.of(), null, "1");
    }

    private AuthorizationDecision plainAllowedDecision() {
        return new AuthorizationDecision(
                AuthorizationEffect.ALLOW, List.of(), List.of(), null, "1");
    }

    private CrudEnforcementDecision<TestEntity> enforcementDecision(CrudOperation operation) {
        return new CrudEnforcementDecision<>(
                7L,
                1L,
                null,
                operation,
                AccessMode.DEFAULT,
                (root, query, builder) -> null,
                (root, query, builder) -> null,
                new CompiledFieldPolicy(Map.of()),
                "rule-version");
    }

    private static final class TestEntity extends BaseEntity {}

    @Test
    @DisplayName("Given 非默认访问模式 When 执行 preflight Then 动作与 access-mode 权限合入单个 L1 ALL")
    void should_combine_action_and_access_mode_permissions_in_one_preflight() {
        // mock 方法
        when(authorizationService.authorize(any())).thenReturn(allowedCrudDecision(Map.of()));

        // 调用
        enforcementService.enforceRequest(
                entry, CrudOperation.GET, AccessMode.ADMIN_MAINTENANCE);

        // 断言
        var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService, times(1)).authorize(captor.capture());
        var request = captor.getValue();
        assertThat(request.plan().l1().mode()).isEqualTo(AuthorizationPlan.FunctionMode.ALL);
        assertThat(request.plan().l1().permissionCodes())
                .containsExactly("system:todo:read", "system:todo:access-mode:admin-maintenance");
        assertThat(request.plan().l3()).isNotNull();
        assertThat(request.plan().l4()).isNotNull();
        assertThat(request.target().action()).isEqualTo("read");
    }

    @Test
    @DisplayName("Given SYSTEM_JOB 缺少内部上下文 When 执行 preflight Then 在读取主体和调用 PDP 前拒绝")
    void should_validate_system_job_context_before_subject_and_pdp() {
        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                enforcementService.enforceRequest(
                                        entry, CrudOperation.GET, AccessMode.SYSTEM_JOB))
                .isInstanceOf(com.xuejiai.aaf.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        verify(operatorContext, never()).currentOwnerId();
        verify(authorizationService, never()).authorize(any());
    }

    @Test
    @DisplayName("Given target payloadDigest 非 64 位 hex When 执行 CREATE target Then 故障关闭且不调用 PDP")
    void should_reject_invalid_payload_digest_before_target_pdp() {
        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                enforcementService.requireCreatedTarget(
                                        entry,
                                        enforcementDecision(CrudOperation.CREATE),
                                        new TestEntity(),
                                        Map.of("ownerId", 7L),
                                        "not-a-sha256"))
                .isInstanceOf(com.xuejiai.aaf.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        verify(authorizationService, never()).authorize(any());
    }
}
