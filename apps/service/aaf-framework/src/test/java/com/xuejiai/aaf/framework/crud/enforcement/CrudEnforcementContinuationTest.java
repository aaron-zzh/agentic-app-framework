package com.xuejiai.aaf.framework.crud.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.CrudAction;
import com.xuejiai.aaf.framework.crud.definition.CrudCapabilityDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationDecision;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationEffect;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationLayer;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationRequest;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.framework.security.authorization.continuation.AuthorizationContinuationResolver;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class CrudEnforcementContinuationTest extends BaseMockitoUnitTest {

    @Mock private OperatorContext operatorContext;
    @Mock private AuthorizationService authorizationService;
    @Mock private CrudResourceCatalogEntry entry;
    @Mock private CrudResourceDefinition<?> definition;
    @Mock private CrudCapabilityDefinition capabilities;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Given continuation 绑定 target L4 When 重试对象 GET Then preflight 普通授权且 target 仅恢复一次")
    void should_not_loop_challenge_between_preflight_and_target() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        bindContinuation(challengeId);
        prepareEnforcement();
        when(authorizationService.selectContinuation(eq(challengeId), any()))
                .thenAnswer(
                        invocation ->
                                ((AuthorizationRequest) invocation.getArgument(1)).plan().l4()
                                                == null
                                        ? AuthorizationService.ContinuationSelection.NOT_MATCH
                                        : AuthorizationService.ContinuationSelection.MATCH);
        when(authorizationService.authorize(any())).thenReturn(allowedCrudDecision());
        when(authorizationService.resume(eq(challengeId), any()))
                .thenReturn(plainAllowedDecision());
        var service = service();
        var entity = new TestEntity();
        entity.setId(99L);
        entity.setOwnerId(7L);

        // 调用
        var preflight =
                service.enforceObjectPreflight(entry, CrudOperation.GET, AccessMode.DEFAULT);
        var allowed =
                service.allowsCurrentTarget(
                        entry, preflight, entity, Map.of("id", 99L, "ownerId", 7L), null);

        // 断言
        assertThat(allowed).isTrue();
        var ordinary = ArgumentCaptor.forClass(AuthorizationRequest.class);
        var resumed = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorizationService, times(2)).selectContinuation(eq(challengeId), any());
        verify(authorizationService, times(1)).authorize(ordinary.capture());
        verify(authorizationService, times(1)).resume(eq(challengeId), resumed.capture());
        assertThat(ordinary.getValue().plan().l4()).isNull();
        assertThat(ordinary.getValue().target().objectId()).isNull();
        assertThat(resumed.getValue().plan().l4()).isNotNull();
        assertThat(resumed.getValue().target().objectId()).isEqualTo("99");
        assertThat(AuthorizationContinuationResolver.resolve()).isEmpty();
    }

    @Test
    @DisplayName("Given continuation 选择不确定 When 对象 preflight 执行 Then 故障关闭且不普通授权")
    void should_fail_closed_when_preflight_continuation_selection_is_indeterminate() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        bindContinuation(challengeId);
        prepareEnforcement();
        when(authorizationService.selectContinuation(eq(challengeId), any()))
                .thenReturn(AuthorizationService.ContinuationSelection.INDETERMINATE);
        var service = service();

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                service.enforceObjectPreflight(
                                        entry, CrudOperation.GET, AccessMode.DEFAULT))
                .isInstanceOf(com.xuejiai.aaf.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        verify(authorizationService, never()).authorize(any());
        verify(authorizationService, never()).resume(any(), any());
    }

    @Test
    @DisplayName("Given continuation 已由 target 成功消费 When 同请求后续授权 Then 不再尝试恢复")
    void should_not_reuse_consumed_continuation_in_same_request() {
        // 准备参数
        var challengeId = UUID.randomUUID();
        bindContinuation(challengeId);
        prepareEnforcement();
        when(authorizationService.selectContinuation(eq(challengeId), any()))
                .thenReturn(AuthorizationService.ContinuationSelection.MATCH);
        when(authorizationService.resume(eq(challengeId), any()))
                .thenReturn(plainAllowedDecision());
        when(authorizationService.authorize(any())).thenReturn(allowedCrudDecision());
        var service = service();
        var entity = new TestEntity();
        entity.setId(99L);

        // 调用
        service.allowsCurrentTarget(entry, enforcementDecision(), entity, Map.of("id", 99L), null);
        service.enforceRequest(entry, CrudOperation.BATCH_READ, AccessMode.DEFAULT);

        // 断言
        verify(authorizationService, times(1)).selectContinuation(eq(challengeId), any());
        verify(authorizationService, times(1)).resume(eq(challengeId), any());
        verify(authorizationService, times(1)).authorize(any());
    }

    private void bindContinuation(UUID challengeId) {
        var request = new MockHttpServletRequest();
        request.addHeader(AuthorizationContinuationResolver.HEADER_NAME, challengeId.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private CrudEnforcementService service() {
        return new CrudEnforcementService(operatorContext, authorizationService);
    }

    private void prepareEnforcement() {
        doReturn(definition).when(entry).definition();
        org.mockito.Mockito.lenient()
                .when(entry.fieldPolicy())
                .thenReturn(new CompiledFieldPolicy(Map.of()));
        when(definition.capabilities()).thenReturn(capabilities);
        when(capabilities.operations())
                .thenReturn(List.of(CrudOperation.GET, CrudOperation.BATCH_READ));
        when(definition.tenantScope()).thenReturn(TenantScope.GLOBAL);
        org.mockito.Mockito.lenient()
                .when(definition.personalScope())
                .thenReturn(PersonalScope.none());
        when(definition.permissionCode(CrudAction.READ)).thenReturn("system:todo:read");
        when(definition.entitySlug()).thenReturn("system.todo");
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(operatorContext.currentOperatorId()).thenReturn(Optional.of(7L));
    }

    private CrudEnforcementDecision<TestEntity> enforcementDecision() {
        return new CrudEnforcementDecision<>(
                7L,
                1L,
                null,
                CrudOperation.GET,
                AccessMode.DEFAULT,
                (root, query, builder) -> null,
                (root, query, builder) -> null,
                new CompiledFieldPolicy(Map.of()),
                "rule-version");
    }

    private AuthorizationDecision allowedCrudDecision() {
        var constraint =
                new CrudDataAuthorizationConstraint(
                        "system.todo",
                        RecordRule.<TestEntity>allowAll("rule-version").specification(),
                        Map.of(),
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
                AuthorizationEffect.ALLOW, List.of(layer), List.of(), null, "snapshot-1");
    }

    private AuthorizationDecision plainAllowedDecision() {
        return new AuthorizationDecision(
                AuthorizationEffect.ALLOW, List.of(), List.of(), null, "snapshot-1");
    }

    private static final class TestEntity extends BaseEntity {}
}
