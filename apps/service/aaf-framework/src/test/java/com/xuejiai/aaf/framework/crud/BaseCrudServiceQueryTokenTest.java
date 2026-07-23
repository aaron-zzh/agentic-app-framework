package com.xuejiai.aaf.framework.crud;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceSnapshot;
import com.xuejiai.aaf.framework.crud.definition.*;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementDecision;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementService;

class BaseCrudServiceQueryTokenTest {

    private final TestCrudService service = new TestCrudService();

    @Test
    @DisplayName("Given 资源排序白名单 When 查询 CRUD 元数据 Then 返回排序能力")
    void should_expose_sortable_fields_in_meta() {
        var meta = service.meta();

        Assertions.assertThat(meta.sortableFields())
                .containsExactlyInAnyOrder("title", "createTime");
    }

    private static final class TestCrudService
            extends BaseCrudService<TestTodo, String, Void, Void, PageParam> {

        private TestCrudService() {
            var key = ResourceKey.of("test.todo");
            var catalog = mock(CrudResourceRegistry.class);
            var entry = mock(CrudResourceCatalogEntry.class);
            var definition = mock(CrudResourceDefinition.class);
            var query = mock(CrudQueryDefinition.class);
            var enforcementService = mock(CrudEnforcementService.class);
            var metaDecision =
                    new CrudEnforcementDecision<TestTodo>(
                            null,
                            null,
                            null,
                            CrudOperation.META,
                            AccessMode.DEFAULT,
                            null,
                            null,
                            null,
                            "test");
            var snapshot =
                    new CrudResourceSnapshot(
                            key,
                            "todo",
                            new CrudResourceDescriptor("待办", "/api/todos", "test:todo"),
                            List.of("get"),
                            List.of("list", "detail"),
                            TenantScope.ORG_REQUIRED,
                            Set.of(CrudResourceExposure.HTTP),
                            CrudResourceDefinition.CURRENT_SCHEMA_VERSION,
                            List.of(),
                            List.of(),
                            false,
                            "test-fingerprint",
                            java.time.Instant.EPOCH);
            when(definition.key()).thenReturn(key);
            when(definition.entitySlug()).thenReturn("todo");
            when(definition.query()).thenReturn(query);
            when(query.filterSchema())
                    .thenReturn(com.xuejiai.aaf.framework.crud.filter.CrudFilterSchema.empty());
            when(query.sortableFields()).thenReturn(Set.of("title", "createTime"));
            when(entry.definition()).thenReturn(definition);
            when(entry.snapshot()).thenReturn(snapshot);
            when(catalog.requireByEntityType(TestTodo.class)).thenReturn(entry);
            when(catalog.require(key)).thenReturn(entry);
            org.mockito.Mockito.doReturn(metaDecision)
                    .when(enforcementService)
                    .enforceRequest(entry, CrudOperation.META, AccessMode.DEFAULT);
            ReflectionTestUtils.setField(this, "crudResourceCatalog", catalog);
            ReflectionTestUtils.setField(this, "crudEnforcementService", enforcementService);
        }

        @Override
        protected CrudEntityRepository<TestTodo> getRepository() {
            return null;
        }

        @Override
        protected String toVO(TestTodo entity) {
            return "";
        }

        @Override
        protected TestTodo toEntity(Void createDTO) {
            return null;
        }

        @Override
        protected void updateEntity(TestTodo entity, Void updateDTO) {}
    }

    private static final class TestTodo extends BaseEntity {}
}
