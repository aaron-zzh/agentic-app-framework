package com.xuejiai.aaf.module.system.entity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDescriptor;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceExposure;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceCatalogEntry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceRegistry;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceSnapshot;
import com.xuejiai.aaf.module.system.entity.domain.EntityDef;
import com.xuejiai.aaf.module.system.entity.repository.EntityDefRepository;
import com.xuejiai.aaf.module.system.entity.vo.EntityDefCreateDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntityDefService 单元测试")
class EntityDefServiceTest {

    @Mock private EntityDefRepository entityDefRepository;
    @Mock private CrudResourceRegistry crudResourceRegistry;

    @InjectMocks private EntityDefService entityDefService;

    @Test
    @DisplayName("Given 非代码配置 When 创建 Then 正常保存")
    void should_create_non_code_configuration() {
        when(entityDefRepository.existsBySlug("custom")).thenReturn(false);
        when(entityDefRepository.save(any(EntityDef.class)))
                .thenAnswer(
                        invocation -> {
                            var entity = invocation.getArgument(0, EntityDef.class);
                            entity.setId(1L);
                            return entity;
                        });

        var result =
                entityDefService.create(
                        new EntityDefCreateDTO(
                                "custom",
                                JsonUtils.readTree(
                                        """
                                        {"fields": [{"name": "unknown"}], "listView": {"columns": ["unknown"]}}
                                        """),
                                true));

        assertThat(result.slug()).isEqualTo("custom");
    }

    @Test
    @DisplayName("Given 代码配置持久化 apiPath When 创建 Then 拒绝")
    void should_reject_persisted_code_api_path() {
        when(entityDefRepository.existsBySlug("todo")).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                entityDefService.create(
                                        new EntityDefCreateDTO(
                                                "todo",
                                                JsonUtils.readTree(
                                                        """
                                                        {"kind": "code", "resource": "system.todo", "apiPath": "/todos"}
                                                        """),
                                                true)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given 代码实体关联未注册资源 When 创建 Then 拒绝")
    void should_reject_unregistered_relationship_resource() {
        when(entityDefRepository.existsBySlug("todo")).thenReturn(false);
        var todoEntry = catalogEntry("system.todo", "todo", "/todos", List.of("id", "title"), true);
        when(crudResourceRegistry.find("system.todo")).thenReturn(Optional.of(todoEntry));
        when(crudResourceRegistry.find("system.unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                entityDefService.create(
                                        new EntityDefCreateDTO(
                                                "todo",
                                                JsonUtils.readTree(
                                                        """
                                                        {
                                                          "kind": "code",
                                                          "resource": "system.todo",
                                                          "fields": [
                                                            {"type": "relationship", "name": "owner", "relationTo": "system.unknown"}
                                                          ]
                                                        }
                                                        """),
                                                true)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given 展示字段与 DTO 写入键不同 When 审计 Todo 定义 Then 通过")
    void should_accept_write_key_not_declared_by_display_vo() {
        var todoDefinition = new EntityDef();
        todoDefinition.setSlug("todo");
        todoDefinition.setEnabled(true);
        todoDefinition.setConfig(
                """
                {
                  "kind": "code",
                  "resource": "system.todo",
                  "fields": [
                    {
                      "type": "relationship",
                      "name": "participants",
                      "relationTo": "system.user",
                      "writeKey": "participantIds"
                    }
                  ]
                }
                """);
        when(entityDefRepository.findAll()).thenReturn(List.of(todoDefinition));
        var todoEntry =
                catalogEntry("system.todo", "todo", "/todos", List.of("participants"), true);
        var userEntry = catalogEntry("system.user", "user", "/system/users", List.of("id"), false);
        when(crudResourceRegistry.find("system.todo")).thenReturn(Optional.of(todoEntry));
        when(crudResourceRegistry.find("system.user")).thenReturn(Optional.of(userEntry));

        entityDefService.auditPersistedCodeDefinitions();
    }

    @Test
    @DisplayName("Given 未声明筛选字段 When 创建代码实体定义 Then 拒绝")
    void should_reject_undeclared_filter_field() {
        when(entityDefRepository.existsBySlug("todo")).thenReturn(false);
        var todoEntry = catalogEntry("system.todo", "todo", "/todos", List.of("id", "title"), true);
        when(crudResourceRegistry.find("system.todo")).thenReturn(Optional.of(todoEntry));

        assertThatThrownBy(
                        () ->
                                entityDefService.create(
                                        new EntityDefCreateDTO(
                                                "todo",
                                                JsonUtils.readTree(
                                                        """
                                                        {
                                                          "kind": "code",
                                                          "resource": "system.todo",
                                                          "fields": [{"type": "text", "name": "title"}],
                                                          "listView": {
                                                            "columns": ["title"],
                                                            "filterFields": ["status"]
                                                          }
                                                        }
                                                        """),
                                                true)))
                .isInstanceOf(BusinessException.class);
    }

    private CrudResourceCatalogEntry catalogEntry(
            String resource,
            String slug,
            String clientApiPath,
            List<String> fields,
            boolean referenceable) {
        var entry = mock(CrudResourceCatalogEntry.class);
        var snapshot =
                new CrudResourceSnapshot(
                        ResourceKey.of(resource),
                        slug,
                        new CrudResourceDescriptor("测试资源", "/api" + clientApiPath, "test:resource"),
                        List.of("get"),
                        List.of("detail"),
                        TenantScope.ORG_REQUIRED,
                        Set.of(CrudResourceExposure.HTTP, CrudResourceExposure.ENTITY_DEF),
                        1,
                        fields,
                        List.of(),
                        referenceable,
                        "test-fingerprint",
                        Instant.EPOCH);
        when(entry.snapshot()).thenReturn(snapshot);
        return entry;
    }
}
