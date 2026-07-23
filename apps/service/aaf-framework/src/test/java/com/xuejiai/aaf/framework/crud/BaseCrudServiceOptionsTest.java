package com.xuejiai.aaf.framework.crud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudQueryDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.crud.enforcement.CompiledFieldPolicy;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementDecision;
import com.xuejiai.aaf.framework.crud.enforcement.CrudEnforcementService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

class BaseCrudServiceOptionsTest extends BaseMockitoUnitTest {

    @Mock private CrudEntityRepository<TestRecord> repository;

    private TestCrudService service;

    @BeforeEach
    void setUp() {
        service = new TestCrudService(repository);
    }

    @Test
    @DisplayName("Given 选择器查询 When 加载选项 Then 合并统一安全范围并返回 ResourceRef")
    void should_apply_enforcement_scope_when_loading_options() {
        // 准备参数
        var record = new TestRecord();
        record.setId(7L);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record)));
        service.configureEnforcement(CrudOperation.OPTIONS);

        // 调用
        var result = service.options("关键字", 20);

        // 断言
        Assertions.assertThat(result).containsExactly(new ResourceRefDTO(7L, "测试记录", null));
        var specificationCaptor = specificationCaptor();
        verify(repository).findAll(specificationCaptor.capture(), any(Pageable.class));
        specificationCaptor
                .getValue()
                .toPredicate(
                        mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class));
        assertThat(service.appliedScopes()).containsExactlyInAnyOrder("option", "enforcement");
    }

    @Test
    @DisplayName("Given 记录可见 When 校验引用 Then 应用统一安全范围")
    void should_apply_enforcement_scope_when_checking_reference_visibility() {
        // mock 方法
        when(repository.findOne(any(Specification.class)))
                .thenReturn(Optional.of(new TestRecord()));
        service.configureEnforcement(CrudOperation.REFERENCE);

        // 调用
        var visible = service.isReferenceVisible(7L);

        // 断言
        Assertions.assertThat(visible).isTrue();
        var specificationCaptor = specificationCaptor();
        verify(repository).findOne(specificationCaptor.capture());
        specificationCaptor
                .getValue()
                .toPredicate(
                        mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class));
        assertThat(service.appliedScopes()).containsExactly("enforcement");
    }

    @Test
    @DisplayName("Given 默认选择器搜索 When 实体有规范字符串字段 Then 对存在字段做模糊匹配")
    void should_search_existing_standard_string_fields_by_default() {
        jakarta.persistence.criteria.Root<TestRecord> root =
                mock(jakarta.persistence.criteria.Root.class);
        var entityType = mock(jakarta.persistence.metamodel.EntityType.class);
        jakarta.persistence.metamodel.Attribute<TestRecord, String> titleAttribute =
                mock(jakarta.persistence.metamodel.Attribute.class);
        jakarta.persistence.metamodel.Attribute<TestRecord, Long> idAttribute =
                mock(jakarta.persistence.metamodel.Attribute.class);
        jakarta.persistence.criteria.Path<Object> titlePath =
                mock(jakarta.persistence.criteria.Path.class);
        jakarta.persistence.criteria.Expression<String> stringPath =
                mock(jakarta.persistence.criteria.Expression.class);
        var predicate = mock(jakarta.persistence.criteria.Predicate.class);
        var criteriaBuilder = mock(CriteriaBuilder.class);

        when(root.getModel()).thenReturn(entityType);
        when(entityType.getAttributes()).thenReturn(Set.of(titleAttribute, idAttribute));
        when(titleAttribute.getName()).thenReturn("title");
        when(titleAttribute.getJavaType()).thenReturn(String.class);
        when(idAttribute.getJavaType()).thenReturn(Long.class);
        when(root.get("title")).thenReturn(titlePath);
        when(titlePath.as(String.class)).thenReturn(stringPath);
        when(criteriaBuilder.lower(stringPath)).thenReturn(stringPath);
        when(criteriaBuilder.like(stringPath, "%需求%")).thenReturn(predicate);

        var result =
                service.defaultOptionSpec(" 需求 ")
                        .toPredicate(root, mock(CriteriaQuery.class), criteriaBuilder);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    @DisplayName("Given 默认选择器搜索 When 实体没有规范字符串字段 Then 安全返回无条件")
    void should_not_fail_when_entity_has_no_standard_search_field() {
        jakarta.persistence.criteria.Root<TestRecord> root =
                mock(jakarta.persistence.criteria.Root.class);
        var entityType = mock(jakarta.persistence.metamodel.EntityType.class);
        jakarta.persistence.metamodel.Attribute<TestRecord, Long> idAttribute =
                mock(jakarta.persistence.metamodel.Attribute.class);

        when(root.getModel()).thenReturn(entityType);
        when(entityType.getAttributes()).thenReturn(Set.of(idAttribute));
        when(idAttribute.getJavaType()).thenReturn(Long.class);

        var result =
                service.defaultOptionSpec("需求")
                        .toPredicate(root, mock(CriteriaQuery.class), mock(CriteriaBuilder.class));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Given 非标准候选字段 When 加载选项 Then 使用该字段作为展示文本")
    void should_display_first_configured_option_search_field() {
        var record = new TestRecord();
        record.setId(7L);
        record.setLabel("高优先级标签");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record)));
        var nonstandardService = new TestCrudService(repository, List.of("label", "value"));
        nonstandardService.configureEnforcement(CrudOperation.OPTIONS);

        var result = nonstandardService.options("高优先级", 20);

        Assertions.assertThat(result).containsExactly(new ResourceRefDTO(7L, "高优先级标签", null));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<Specification<TestRecord>> specificationCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Specification.class);
    }

    private static final class TestCrudService
            extends BaseCrudService<TestRecord, String, Void, Void, PageParam> {

        private final CrudEntityRepository<TestRecord> repository;
        private final List<String> nonstandardOptionSearchFields;
        private final Set<String> appliedScopes = new java.util.LinkedHashSet<>();

        private TestCrudService(CrudEntityRepository<TestRecord> repository) {
            this(repository, null);
        }

        private TestCrudService(
                CrudEntityRepository<TestRecord> repository,
                List<String> nonstandardOptionSearchFields) {
            this.repository = repository;
            this.nonstandardOptionSearchFields = nonstandardOptionSearchFields;
        }

        private void configureEnforcement(CrudOperation operation) {
            var catalog = mock(CrudResourceRegistry.class);
            var entry = mock(CrudResourceCatalogEntry.class);
            var enforcementService = mock(CrudEnforcementService.class);
            var fieldPolicy = mock(CompiledFieldPolicy.class);
            var decision =
                    new CrudEnforcementDecision<TestRecord>(
                            null,
                            null,
                            null,
                            operation,
                            AccessMode.DEFAULT,
                            marker("tenant"),
                            marker("enforcement"),
                            fieldPolicy,
                            "test");
            when(catalog.requireByEntityType(TestRecord.class)).thenReturn(entry);
            if (operation == CrudOperation.OPTIONS) {
                var definition = mock(CrudResourceDefinition.class);
                var query = mock(CrudQueryDefinition.class);
                when(definition.query()).thenReturn(query);
                when(query.defaultSort()).thenReturn(Sort.by("id").descending());
                when(entry.definition()).thenReturn(definition);
            }
            org.mockito.Mockito.doReturn(decision)
                    .when(enforcementService)
                    .enforceRequest(entry, operation, AccessMode.DEFAULT);
            ReflectionTestUtils.setField(this, "crudResourceCatalog", catalog);
            ReflectionTestUtils.setField(this, "crudEnforcementService", enforcementService);
        }

        private Set<String> appliedScopes() {
            return Set.copyOf(appliedScopes);
        }

        @Override
        protected CrudEntityRepository<TestRecord> getRepository() {
            return repository;
        }

        @Override
        protected String toVO(TestRecord entity) {
            return "";
        }

        @Override
        protected TestRecord toEntity(Void createDTO) {
            return null;
        }

        @Override
        protected void updateEntity(TestRecord entity, Void updateDTO) {}

        @Override
        protected Specification<TestRecord> buildOptionSpec(String keyword) {
            return marker("option");
        }

        @Override
        protected List<String> optionSearchFields() {
            return nonstandardOptionSearchFields == null
                    ? super.optionSearchFields()
                    : nonstandardOptionSearchFields;
        }

        @Override
        protected String displayName(TestRecord entity) {
            return "测试记录";
        }

        private Specification<TestRecord> defaultOptionSpec(String keyword) {
            return super.buildOptionSpec(keyword);
        }

        private Specification<TestRecord> marker(String name) {
            return (root, query, builder) -> {
                appliedScopes.add(name);
                return null;
            };
        }
    }

    private static final class TestRecord extends BaseEntity {
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
