package com.xuejiai.aaf.framework.crud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.definition.CrudMutationDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.FieldCapability;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.CompiledFieldPolicy;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementDecision;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementService;
import com.xuejiai.aaf.framework.crud.relation.GenericRelationHandler;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.crud.view.CrudViewPlan;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.Table;

class BaseCrudServiceObjectAuthorizationTest extends BaseMockitoUnitTest {

    @Mock private CrudEntityRepository<TestEntity> repository;
    @Mock private CrudResourceRegistry registry;
    @Mock private CrudResourceCatalogEntry entry;
    @Mock private CrudResourceDefinition<?> definition;
    @Mock private CrudEnforcementService enforcementService;
    @Mock private GenericRelationHandler genericRelationHandler;
    @Mock private EntityManager entityManager;
    @Mock private Query query;

    private TestCrudService service;

    @BeforeEach
    void setUp() {
        service = new TestCrudService(repository);
        org.mockito.Mockito.lenient()
                .when(registry.requireByEntityType(TestEntity.class))
                .thenReturn(entry);
        org.mockito.Mockito.lenient().doReturn(definition).when(entry).definition();
        org.mockito.Mockito.lenient()
                .when(entry.viewPlans())
                .thenReturn(
                        Map.of(
                                "detail",
                                new CrudViewPlan("detail", Set.of("status"), Map.of(), "")));
        org.mockito.Mockito.lenient()
                .when(definition.tenantScope())
                .thenReturn(TenantScope.ORG_REQUIRED);
        org.mockito.Mockito.lenient()
                .when(definition.personalScope())
                .thenReturn(PersonalScope.none());
        org.mockito.Mockito.lenient().when(definition.references()).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(definition.relations()).thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(definition.mutation())
                .thenReturn(
                        new CrudMutationDefinition(
                                Set.of("status", "metadata"),
                                Set.of("status", "metadata"),
                                Map.of()));
        ReflectionTestUtils.setField(service, "crudResourceRegistry", registry);
        ReflectionTestUtils.setField(service, "crudEnforcementService", enforcementService);
        ReflectionTestUtils.setField(service, "genericRelationHandler", genericRelationHandler);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    @DisplayName("Given CREATE 请求 When 应用租户和 owner Then target L4 在保存前接收 CREATED 服务端快照")
    void should_authorize_created_snapshot_after_tenant_owner_and_before_save() {
        // 准备参数
        var decision = decision(CrudOperation.CREATE);
        when(enforcementService.<TestEntity>enforceObjectPreflight(
                        entry, CrudOperation.CREATE, AccessMode.DEFAULT))
                .thenReturn(decision);
        when(repository.save(any()))
                .thenAnswer(
                        invocation -> {
                            TestEntity entity = invocation.getArgument(0);
                            entity.setId(88L);
                            return entity;
                        });

        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("z", 2);
        metadata.put("a", 1);
        var request = new MutationDTO("DRAFT", metadata);

        // 调用
        var result = service.create(request);

        // 断言
        assertThat(result).isEqualTo("DRAFT");
        var attributes = mapCaptor();
        var digest = ArgumentCaptor.forClass(String.class);
        var order = inOrder(enforcementService, repository);
        order.verify(enforcementService)
                .requireCreatedTarget(
                        eq(entry),
                        eq(decision),
                        any(TestEntity.class),
                        attributes.capture(),
                        digest.capture());
        order.verify(repository).save(any(TestEntity.class));
        assertThat(digest.getValue()).isEqualTo(payloadDigest(request));
        assertThat(attributes.getValue())
                .containsEntry("orgId", BigDecimal.valueOf(3L))
                .containsEntry("ownerId", BigDecimal.valueOf(7L))
                .containsEntry("status", "DRAFT")
                .doesNotContainKey("id");
    }

    @Test
    @DisplayName("Given 批量 CREATE 请求 When 保存实体 Then 一次批次 target L4 绑定全部 CREATED 快照与原请求摘要")
    void should_authorize_batch_create_with_single_target_before_save() {
        // 准备参数
        var decision = decision(CrudOperation.CREATE);
        when(enforcementService.<TestEntity>enforceObjectPreflight(
                        entry, CrudOperation.CREATE, AccessMode.DEFAULT))
                .thenReturn(decision);
        var first = new MutationDTO("DRAFT", Map.of("source", "first"));
        var second = new MutationDTO("DRAFT", Map.of("source", "second"));

        // 调用
        service.createBatch(List.of(first, second));

        // 断言
        var created = BaseCrudServiceObjectAuthorizationTest.<Map<String, Object>>listCaptor();
        var digests = BaseCrudServiceObjectAuthorizationTest.<String>listCaptor();
        var order = inOrder(enforcementService, repository);
        order.verify(enforcementService)
                .requireCreatedBatchTarget(
                        eq(entry), eq(decision), created.capture(), digests.capture());
        order.verify(repository).saveAll(any());
        verify(enforcementService)
                .enforceObjectPreflight(entry, CrudOperation.CREATE, AccessMode.DEFAULT);
        verify(enforcementService, never())
                .enforceRequest(entry, CrudOperation.CREATE, AccessMode.DEFAULT);
        verify(enforcementService, never())
                .requireCreatedTarget(eq(entry), eq(decision), any(TestEntity.class), any(), any());
        assertThat(digests.getValue()).containsExactly(payloadDigest(first), payloadDigest(second));
        assertThat(created.getValue())
                .hasSize(2)
                .allSatisfy(
                        attributes ->
                                assertThat(attributes)
                                        .containsEntry("orgId", BigDecimal.valueOf(3L))
                                        .containsEntry("ownerId", BigDecimal.valueOf(7L))
                                        .containsEntry("status", "DRAFT")
                                        .doesNotContainKey("id"));
    }

    @Test
    @DisplayName("Given UPDATE 实体 When 应用修改 Then target L4 一次接收修改前 CURRENT 与修改后 PROPOSED")
    void should_capture_current_before_update_and_proposed_after_update() {
        // 准备参数
        var decision = decision(CrudOperation.UPDATE);
        var entity = entity(99L, "OPEN");
        when(enforcementService.<TestEntity>enforceObjectPreflight(
                        entry, CrudOperation.UPDATE, AccessMode.DEFAULT))
                .thenReturn(decision);
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(entity));

        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("z", 2);
        metadata.put("a", 1);
        var request = new MutationDTO("DONE", metadata);

        // 调用
        var result = service.update(99L, request);

        // 断言
        assertThat(result).isEqualTo("DONE");
        var current = mapCaptor();
        var proposed = mapCaptor();
        var digest = ArgumentCaptor.forClass(String.class);
        verify(enforcementService)
                .requireUpdatedTarget(
                        eq(entry),
                        eq(decision),
                        eq("99"),
                        current.capture(),
                        proposed.capture(),
                        digest.capture(),
                        eq("UPDATE"),
                        eq(Set.of("status", "metadata")));
        assertThat(digest.getValue()).isEqualTo(payloadDigest(request));
        verify(entityManager).refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        assertThat(current.getValue())
                .containsEntry("id", BigDecimal.valueOf(99L))
                .containsEntry("status", "OPEN");
        assertThat(proposed.getValue())
                .containsEntry("id", BigDecimal.valueOf(99L))
                .containsEntry("status", "DONE");
    }

    @Test
    @DisplayName("Given ARCHIVE 批量请求 When 执行默认归档 Then 只使用 ARCHIVE 决策完成数据变更")
    void should_archive_with_single_archive_decision() {
        // 准备参数
        var decision = decision(CrudOperation.ARCHIVE);
        var entities = List.of(entity(1L, "OPEN"), entity(2L, "DONE"));
        when(enforcementService.<TestEntity>enforceRequest(
                        entry, CrudOperation.ARCHIVE, AccessMode.DEFAULT))
                .thenReturn(decision);
        when(repository.findAll(any(Specification.class))).thenReturn(entities);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
        when(query.setParameter("ids", List.of(1L, 2L))).thenReturn(query);

        // 调用
        service.archive(List.of(1L, 2L));

        // 断言
        verify(enforcementService).enforceRequest(entry, CrudOperation.ARCHIVE, AccessMode.DEFAULT);
        verify(enforcementService, never())
                .enforceRequest(entry, CrudOperation.DELETE_BATCH, AccessMode.DEFAULT);
        verify(genericRelationHandler).cleanupSourceLinks(List.of(), List.of(1L, 2L));
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("Given 管理清理条件 When 批量删除匹配记录 Then 只执行一次 DELETE_BATCH PEP")
    void should_delete_matching_entities_with_single_batch_decision() {
        // 准备参数
        var decision = decision(CrudOperation.DELETE_BATCH, AccessMode.ADMIN_MAINTENANCE);
        var entities = List.of(entity(3L, "DONE"), entity(4L, "DONE"));
        when(enforcementService.<TestEntity>enforceRequest(
                        entry, CrudOperation.DELETE_BATCH, AccessMode.ADMIN_MAINTENANCE))
                .thenReturn(decision);
        when(repository.findAll(any(Specification.class))).thenReturn(entities);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
        when(query.setParameter("ids", List.of(3L, 4L))).thenReturn(query);

        // 调用
        var deleted = service.deleteMatching((root, criteriaQuery, builder) -> null);

        // 断言
        assertThat(deleted).isEqualTo(2);
        verify(enforcementService)
                .enforceRequest(entry, CrudOperation.DELETE_BATCH, AccessMode.ADMIN_MAINTENANCE);
        verify(enforcementService, never())
                .enforceObjectPreflight(entry, CrudOperation.DELETE, AccessMode.ADMIN_MAINTENANCE);
        verify(genericRelationHandler).cleanupSourceLinks(List.of(), List.of(3L, 4L));
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("Given GET 详情方法 When 检查事务声明 Then continuation 消费与读取映射共享事务")
    void should_declare_transaction_boundary_for_get_details() throws Exception {
        assertThat(
                        BaseCrudService.class
                                .getMethod("getById", Long.class)
                                .getAnnotation(Transactional.class))
                .isNotNull();
        assertThat(
                        BaseCrudService.class
                                .getMethod("getById", Long.class, String.class, String.class)
                                .getAnnotation(Transactional.class))
                .isNotNull();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<List<T>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private CrudEnforcementDecision<TestEntity> decision(CrudOperation operation) {
        return decision(operation, AccessMode.DEFAULT);
    }

    private CrudEnforcementDecision<TestEntity> decision(
            CrudOperation operation, AccessMode accessMode) {
        var policy =
                new CompiledFieldPolicy(
                        Map.of(
                                "status", EnumSet.allOf(FieldCapability.class),
                                "metadata", EnumSet.allOf(FieldCapability.class)));
        return new CrudEnforcementDecision<>(
                7L,
                3L,
                null,
                operation,
                accessMode,
                (root, criteriaQuery, builder) -> null,
                (root, criteriaQuery, builder) -> null,
                policy,
                "rule-version");
    }

    private TestEntity entity(Long id, String status) {
        var entity = new TestEntity();
        entity.setId(id);
        entity.setOrgId(3L);
        entity.setOwnerId(7L);
        entity.setStatus(status);
        return entity;
    }

    private String payloadDigest(MutationDTO request) {
        var stable = new TreeMap<String, Object>();
        stable.put("metadata", new TreeMap<>(request.metadata()));
        stable.put("status", request.status());
        try {
            var bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(
                                    JsonUtils.toJsonString(stable)
                                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException cause) {
            throw new IllegalStateException(cause);
        }
    }

    private record MutationDTO(String status, Map<String, Object> metadata) {}

    private static final class TestCrudService
            extends BaseCrudService<TestEntity, String, MutationDTO, MutationDTO, PageParam> {

        private final CrudEntityRepository<TestEntity> repository;

        private TestCrudService(CrudEntityRepository<TestEntity> repository) {
            this.repository = repository;
        }

        @Override
        protected CrudEntityRepository<TestEntity> getRepository() {
            return repository;
        }

        @Override
        protected String toVO(TestEntity entity) {
            return entity.getStatus();
        }

        @Override
        protected TestEntity toEntity(MutationDTO createDTO) {
            var entity = new TestEntity();
            entity.setStatus(createDTO.status());
            return entity;
        }

        @Override
        protected void updateEntity(TestEntity entity, MutationDTO updateDTO) {
            entity.setStatus(updateDTO.status());
        }

        @Override
        protected Map<String, Object> authorizationAttributes(TestEntity entity) {
            var attributes = new LinkedHashMap<>(super.authorizationAttributes(entity));
            attributes.put("status", entity.getStatus());
            return attributes;
        }

        private long deleteMatching(Specification<TestEntity> spec) {
            return deleteMatchingWithAccess(spec, AccessMode.ADMIN_MAINTENANCE);
        }
    }

    @Table(name = "test_entity")
    private static final class TestEntity extends BaseEntity {
        private String status;

        private String getStatus() {
            return status;
        }

        private void setStatus(String status) {
            this.status = status;
        }
    }
}
