package com.xuejiai.aaf.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.xuejiai.aaf.common.enums.OperatorType;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.CrudAction;
import com.xuejiai.aaf.framework.crud.definition.CrudCapabilityDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.CompiledFieldPolicy;
import com.xuejiai.aaf.framework.crud.enforcement.CrudDataAuthorizationProvider;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementService;
import com.xuejiai.aaf.framework.crud.enforcement.RecordRule;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationAuditSink;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationChallengeRequiredException;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationChallengeStore;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPolicy;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPolicyProvider;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationService;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationTarget;
import com.xuejiai.aaf.framework.security.authorization.DataAuthorizationProvider;
import com.xuejiai.aaf.framework.security.authorization.DefaultAuthorizationService;
import com.xuejiai.aaf.framework.security.authorization.FieldAccessSupport;
import com.xuejiai.aaf.framework.security.authorization.FunctionPermissionChecker;
import com.xuejiai.aaf.framework.security.authorization.PolicyDslCompiler;
import com.xuejiai.aaf.framework.security.authorization.PolicyExpressionEvaluator;
import com.xuejiai.aaf.framework.security.authorization.PolicyFactSchema;
import com.xuejiai.aaf.framework.security.authorization.RecordRuleSupport;
import com.xuejiai.aaf.framework.security.authorization.RelationPermissionChecker;
import com.xuejiai.aaf.framework.security.authorization.continuation.AuthorizationContinuationResolver;
import com.xuejiai.aaf.module.system.authorization.AccessPolicy;
import com.xuejiai.aaf.module.system.authorization.AccessPolicyRepository;
import com.xuejiai.aaf.module.system.authorization.AuthorizationChallengeRepository;

@SpringBootTest
@ActiveProfiles("test")
class AuthorizationContinuationTransactionIT {

    private static final long SUBJECT_ID = 991_001L;
    private static final String RESOURCE = "test.transactional-create";
    private static final String MARKER_PREFIX = "continuation-transaction-it-";

    @Autowired private AuthorizationChallengeStore challengeStore;
    @Autowired private AuthorizationChallengeRepository challengeRepository;
    @Autowired private AccessPolicyRepository accessPolicyRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private AuthorizationService authorizationService;
    private CrudEnforcementService enforcementService;
    private CrudResourceCatalogEntry entry;
    private UUID challengeId;

    @BeforeEach
    void setUp() {
        OrgContext.clear();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                String.valueOf(SUBJECT_ID),
                                "test",
                                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        var operatorContext = fixedOperatorContext();
        authorizationService = authorizationService(operatorContext);
        enforcementService = enforcementService(operatorContext, authorizationService);
        entry = entry();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        if (challengeId != null) {
            challengeRepository.deleteById(challengeId);
        }
        OrgContext.runIgnoring(
                () -> {
                    var markers =
                            accessPolicyRepository.findAllByOrderByPriority().stream()
                                    .filter(policy -> policy.getName().startsWith(MARKER_PREFIX))
                                    .toList();
                    accessPolicyRepository.deleteAll(markers);
                });
        OrgContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Given 已批准 CREATE challenge When 消费后业务写失败 Then 消费和数据共同回滚且原请求可再次成功")
    void should_rollback_consumption_with_business_write_and_allow_same_request_retry() {
        // 准备参数
        var markerName = MARKER_PREFIX + UUID.randomUUID();
        var required =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> executeCreate(markerName, false),
                        AuthorizationChallengeRequiredException.class);
        assertThat(required).isNotNull();
        challengeId = required.getChallengeId();
        assertThat(challengeRepository.findById(challengeId).orElseThrow().getStatus())
                .isEqualTo("PENDING");
        assertThat(authorizationService.approveChallenge(challengeId)).isTrue();
        bindContinuation(challengeId);

        // 调用
        assertThatThrownBy(() -> executeCreate(markerName, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟授权后业务写失败");

        // 断言
        var rolledBack = challengeRepository.findById(challengeId).orElseThrow();
        assertThat(rolledBack.getStatus()).isEqualTo("APPROVED");
        assertThat(rolledBack.getConsumedAt()).isNull();
        assertThat(markerExists(markerName)).isFalse();

        bindContinuation(challengeId);
        executeCreate(markerName, false);

        var consumed = challengeRepository.findById(challengeId).orElseThrow();
        assertThat(consumed.getStatus()).isEqualTo("CONSUMED");
        assertThat(consumed.getConsumedAt()).isNotNull();
        assertThat(markerExists(markerName)).isTrue();
    }

    private void executeCreate(String markerName, boolean failAfterWrite) {
        transactionTemplate.executeWithoutResult(
                ignored -> {
                    var preflight =
                            enforcementService.enforceObjectPreflight(
                                    entry, CrudOperation.CREATE, AccessMode.DEFAULT);
                    enforcementService.requireCreatedTarget(
                            entry,
                            preflight,
                            new TransactionEntity(),
                            Map.of("ownerId", SUBJECT_ID, "marker", markerName),
                            "e".repeat(64));
                    OrgContext.runIgnoring(
                            () -> {
                                accessPolicyRepository.saveAndFlush(marker(markerName));
                            });
                    if (failAfterWrite) {
                        throw new IllegalStateException("模拟授权后业务写失败");
                    }
                });
    }

    private boolean markerExists(String markerName) {
        return OrgContext.runIgnoring(
                () ->
                        accessPolicyRepository.findAllByOrderByPriority().stream()
                                .anyMatch(policy -> markerName.equals(policy.getName())));
    }

    private AccessPolicy marker(String name) {
        var marker = new AccessPolicy();
        marker.setName(name);
        marker.setConditionJson("{}");
        marker.setFactSchemaJson("{}");
        marker.setEffect("ALLOW");
        marker.setPriority(999);
        marker.setTargetResource("test.marker");
        marker.setTargetAction("create");
        marker.setLifecycle("DRAFT");
        marker.setPublishedVersion(0L);
        return marker;
    }

    private AuthorizationService authorizationService(OperatorContext operatorContext) {
        var policy =
                new AuthorizationPolicy(
                        77L,
                        1L,
                        "事务恢复测试策略",
                        new AuthorizationTarget(RESOURCE, "create", null),
                        10,
                        AuthorizationPolicy.Lifecycle.ENFORCE,
                        AuthorizationPolicy.PolicyEffect.CHALLENGE,
                        "{}");
        AuthorizationPolicyProvider policyProvider =
                target ->
                        new AuthorizationPolicy.Snapshot(
                                "snapshot-transaction-1",
                                PolicyFactSchema.builtInsOnly(),
                                List.of(policy));
        RecordRuleSupport recordRuleSupport =
                new RecordRuleSupport() {
                    @Override
                    public <T> RecordRule<T> compile(String resourceKey, Long subjectId) {
                        return RecordRule.allowAll("transaction-rule-1");
                    }
                };
        FieldAccessSupport fieldAccessSupport = (resourceKey, subjectId) -> Map.of();
        DataAuthorizationProvider dataAuthorizationProvider =
                new CrudDataAuthorizationProvider(
                        provider(RecordRuleSupport.class, recordRuleSupport),
                        provider(FieldAccessSupport.class, fieldAccessSupport));
        return new DefaultAuthorizationService(
                operatorContext,
                emptyProvider(FunctionPermissionChecker.class),
                emptyProvider(RelationPermissionChecker.class),
                provider(DataAuthorizationProvider.class, dataAuthorizationProvider),
                provider(AuthorizationPolicyProvider.class, policyProvider),
                provider(AuthorizationChallengeStore.class, challengeStore),
                emptyProvider(AuthorizationAuditSink.class),
                new PolicyDslCompiler(),
                new PolicyExpressionEvaluator());
    }

    private CrudEnforcementService enforcementService(
            OperatorContext operatorContext, AuthorizationService service) {
        return new CrudEnforcementService(operatorContext, service);
    }

    @SuppressWarnings("unchecked")
    private CrudResourceCatalogEntry entry() {
        var catalogEntry = mock(CrudResourceCatalogEntry.class);
        var definition = mock(CrudResourceDefinition.class);
        var capabilities = mock(CrudCapabilityDefinition.class);
        when(catalogEntry.definition()).thenReturn(definition);
        when(catalogEntry.fieldPolicy()).thenReturn(new CompiledFieldPolicy(Map.of()));
        when(definition.capabilities()).thenReturn(capabilities);
        when(capabilities.operations()).thenReturn(List.of(CrudOperation.CREATE));
        when(definition.tenantScope()).thenReturn(TenantScope.GLOBAL);
        when(definition.personalScope()).thenReturn(PersonalScope.none());
        when(definition.permissionCode(CrudAction.CREATE)).thenReturn("test:transaction:create");
        when(definition.entitySlug()).thenReturn(RESOURCE);
        return catalogEntry;
    }

    private OperatorContext fixedOperatorContext() {
        return new OperatorContext() {
            @Override
            public Optional<Long> currentOperatorId() {
                return Optional.of(SUBJECT_ID);
            }

            @Override
            public OperatorType currentOperatorType() {
                return OperatorType.HUMAN;
            }

            @Override
            public Optional<Long> currentOwnerId() {
                return Optional.of(SUBJECT_ID);
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }
        };
    }

    private void bindContinuation(UUID id) {
        var request = new MockHttpServletRequest();
        request.addHeader(AuthorizationContinuationResolver.HEADER_NAME, id.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        var factory = new StaticListableBeanFactory();
        factory.addBean(type.getName(), bean);
        return factory.getBeanProvider(type);
    }

    private <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
    }

    private static final class TransactionEntity extends BaseEntity {}
}
