package com.xuejiai.aaf.framework.crud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.CompiledFieldPolicy;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementDecision;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementService;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

class BaseCrudServiceRelationAuthorizationTest extends BaseMockitoUnitTest {

    @Mock private CrudEntityRepository<TestEntity> repository;
    @Mock private CrudResourceRegistry registry;
    @Mock private CrudResourceCatalogEntry entry;
    @Mock private CrudResourceDefinition<?> definition;
    @Mock private CrudEnforcementService enforcementService;

    private TestCrudService service;
    private CrudEnforcementDecision<TestEntity> decision;

    @BeforeEach
    void setUp() {
        service = new TestCrudService(repository);
        decision =
                new CrudEnforcementDecision<>(
                        7L,
                        1L,
                        null,
                        CrudOperation.GET,
                        AccessMode.DEFAULT,
                        service.marker("tenant"),
                        service.marker("l3"),
                        new CompiledFieldPolicy(Map.of()),
                        "rule-version");
        when(registry.requireByEntityType(TestEntity.class)).thenReturn(entry);
        when(entry.definition()).thenReturn(definition);
        when(definition.displayName()).thenReturn("测试资源");
        when(enforcementService.enforceObjectPreflight(
                        entry, CrudOperation.GET, AccessMode.DEFAULT))
                .thenReturn(decision);
        ReflectionTestUtils.setField(service, "crudResourceRegistry", registry);
        ReflectionTestUtils.setField(service, "crudEnforcementService", enforcementService);
    }

    @Test
    @DisplayName("Given L3 未命中且显式关系允许 When 读取单对象 Then 先查 L3 再仅按 tenant scope 重试")
    void should_retry_with_tenant_scope_after_explicit_relation_allows() {
        // 准备参数
        var entity = new TestEntity();
        entity.setId(99L);
        when(repository.findOne(any(Specification.class)))
                .thenReturn(Optional.empty(), Optional.of(entity));
        when(enforcementService.allowsCurrentTarget(
                        any(), any(), any(), any(), any()))
                .thenReturn(true);

        // 调用
        var result = service.load(99L);

        // 断言
        assertThat(result).isSameAs(entity);
        verify(enforcementService)
                .allowsCurrentTarget(
                        eq(entry), eq(decision), eq(entity), any(), eq(service.requirement()));
        var captor = specificationCaptor();
        verify(repository, times(2)).findOne(captor.capture());
        captor.getAllValues()
                .forEach(
                        specification ->
                                specification.toPredicate(
                                        mock(Root.class),
                                        mock(CriteriaQuery.class),
                                        mock(CriteriaBuilder.class)));
        assertThat(service.appliedScopes()).containsExactly("l3", "tenant");
    }

    @Test
    @DisplayName("Given L3 未命中且关系 Provider 拒绝 When 读取单对象 Then 不查询 tenant-only 范围")
    void should_not_retry_tenant_scope_when_relation_is_denied() {
        // 准备参数
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());
        when(enforcementService.allowsCurrentTarget(
                        any(), any(), any(), any(), any()))
                .thenReturn(false);

        // 调用 + 断言
        assertThatThrownBy(() -> service.load(99L)).isInstanceOf(BusinessException.class);
        verify(repository).findOne(any(Specification.class));
    }

    @Test
    @DisplayName("Given 关系授权故障 When 默认读取 Then 原样失败且不执行 tenant-only 查询")
    void should_not_retry_tenant_scope_when_relation_authorization_faults() {
        // 准备参数
        var failure = new IllegalStateException("relation provider unavailable");
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());
        when(enforcementService.allowsCurrentTarget(
                        any(), any(), any(), any(), any()))
                .thenThrow(failure);

        // 调用 + 断言
        assertThatThrownBy(() -> service.load(99L)).isSameAs(failure);
        verify(repository).findOne(any(Specification.class));
    }

    @Test
    @DisplayName("Given UPDATE 或 DELETE 的 L3 范围未命中 When 加载实体 Then 不走关系授权与 tenant-only 重试")
    void should_not_use_relation_fallback_for_update_or_delete() {
        // 准备参数
        for (var operation : Set.of(CrudOperation.UPDATE, CrudOperation.DELETE)) {
            when(enforcementService.enforceObjectPreflight(
                            entry, operation, AccessMode.DEFAULT))
                    .thenReturn(decision(operation));
        }
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        // 调用 + 断言
        for (var operation : Set.of(CrudOperation.UPDATE, CrudOperation.DELETE)) {
            assertThatThrownBy(() -> service.load(99L, operation))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(404);
        }
        verify(repository, times(2)).findOne(any(Specification.class));
        org.mockito.Mockito.verify(enforcementService, org.mockito.Mockito.never())
                .allowsCurrentTarget(any(), any(), any(), any(), any());
    }

    private CrudEnforcementDecision<TestEntity> decision(CrudOperation operation) {
        return new CrudEnforcementDecision<>(
                7L,
                1L,
                null,
                operation,
                AccessMode.DEFAULT,
                service.marker("tenant-" + operation),
                service.marker("l3-" + operation),
                new CompiledFieldPolicy(Map.of()),
                "rule-version");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<Specification<TestEntity>> specificationCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Specification.class);
    }

    private static final class TestCrudService
            extends BaseCrudService<TestEntity, String, Void, Void, PageParam> {

        private final CrudEntityRepository<TestEntity> repository;
        private final Set<String> appliedScopes = new LinkedHashSet<>();
        private final AuthorizationPlan.RelationRequirement requirement =
                new AuthorizationPlan.RelationRequirement("test", "99", "can_read");

        private TestCrudService(CrudEntityRepository<TestEntity> repository) {
            this.repository = repository;
        }

        private TestEntity load(Long id) {
            return requireEntity(id);
        }

        private TestEntity load(Long id, CrudOperation operation) {
            return requireEntity(id, operation, AccessMode.DEFAULT);
        }

        private AuthorizationPlan.RelationRequirement requirement() {
            return requirement;
        }

        private java.util.List<String> appliedScopes() {
            return java.util.List.copyOf(appliedScopes);
        }

        private Specification<TestEntity> marker(String name) {
            return (root, query, builder) -> {
                appliedScopes.add(name);
                return null;
            };
        }

        @Override
        protected AuthorizationPlan.RelationRequirement relationRequirement(
                Long id, CrudOperation operation) {
            return operation == CrudOperation.GET ? requirement : null;
        }

        @Override
        protected CrudEntityRepository<TestEntity> getRepository() {
            return repository;
        }

        @Override
        protected String toVO(TestEntity entity) {
            return "";
        }

        @Override
        protected TestEntity toEntity(Void createDTO) {
            return null;
        }

        @Override
        protected void updateEntity(TestEntity entity, Void updateDTO) {}
    }

    private static final class TestEntity extends BaseEntity {}
}
