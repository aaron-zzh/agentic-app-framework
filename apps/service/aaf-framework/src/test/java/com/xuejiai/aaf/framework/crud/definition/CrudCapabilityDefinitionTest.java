package com.xuejiai.aaf.framework.crud.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;

class CrudCapabilityDefinitionTest {

    @Test
    @DisplayName("Given 创建更新类型均为 Void When 推导能力 Then 不声明通用写操作")
    void should_exclude_write_operations_when_resource_is_readonly() {
        var types = types(Void.class, Void.class);

        var operations = CrudCapabilityDefinition.forTypes(types).operations();

        assertThat(operations)
                .contains(CrudOperation.GET, CrudOperation.QUERY, CrudOperation.EXPORT)
                .doesNotContain(
                        CrudOperation.CREATE,
                        CrudOperation.UPDATE,
                        CrudOperation.DELETE,
                        CrudOperation.DELETE_BATCH,
                        CrudOperation.ARCHIVE,
                        CrudOperation.RESTORE);
    }

    @Test
    @DisplayName("Given 仅创建 DTO 可用 When 推导能力 Then 只声明实际可用的通用写操作")
    void should_derive_operations_when_only_create_type_is_writable() {
        var types = types(CreateDTO.class, Void.class);

        var operations = CrudCapabilityDefinition.forTypes(types).operations();

        assertThat(operations)
                .contains(
                        CrudOperation.CREATE,
                        CrudOperation.IMPORT,
                        CrudOperation.VALIDATE,
                        CrudOperation.DELETE,
                        CrudOperation.DELETE_BATCH,
                        CrudOperation.ARCHIVE)
                .doesNotContain(CrudOperation.UPDATE, CrudOperation.RESTORE);
    }

    @Test
    @DisplayName("Given 仅更新 DTO 可用 When 推导能力 Then 不声明创建类操作")
    void should_derive_operations_when_only_update_type_is_writable() {
        var types = types(Void.class, UpdateDTO.class);

        var operations = CrudCapabilityDefinition.forTypes(types).operations();

        assertThat(operations)
                .contains(
                        CrudOperation.UPDATE,
                        CrudOperation.RESTORE,
                        CrudOperation.DELETE,
                        CrudOperation.DELETE_BATCH,
                        CrudOperation.ARCHIVE)
                .doesNotContain(CrudOperation.CREATE, CrudOperation.IMPORT, CrudOperation.VALIDATE);
    }

    @Test
    @DisplayName("Given 类型推导默认能力 When 移除业务禁用操作 Then 仅产生差量定义")
    void should_remove_disabled_operations_without_changing_defaults() {
        var defaults = CrudCapabilityDefinition.forTypes(types(CreateDTO.class, UpdateDTO.class));

        var customized =
                defaults.without(
                        CrudOperation.IMPORT, CrudOperation.RESTORE, CrudOperation.ARCHIVE);

        assertThat(customized.operations())
                .doesNotContain(CrudOperation.IMPORT, CrudOperation.RESTORE, CrudOperation.ARCHIVE)
                .contains(
                        CrudOperation.CREATE,
                        CrudOperation.UPDATE,
                        CrudOperation.DELETE,
                        CrudOperation.DELETE_BATCH);
        assertThat(defaults.operations())
                .contains(CrudOperation.IMPORT, CrudOperation.RESTORE, CrudOperation.ARCHIVE);
    }

    @Test
    @DisplayName("Given 旧批量删除能力名 When 解析 Then 拒绝未知 capability")
    void should_reject_legacy_batch_delete_capability() {
        assertThat(CrudOperation.fromCapability("deleteBatch"))
                .isEqualTo(CrudOperation.DELETE_BATCH);
        assertThatThrownBy(() -> CrudOperation.fromCapability("batchDelete"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知 CRUD capability");
    }

    private CrudResourceTypeContract<TestEntity> types(Class<?> createType, Class<?> updateType) {
        return new CrudResourceTypeContract<>(
                TestEntity.class, createType, updateType, TestView.class, TestPageParam.class);
    }

    private static final class TestEntity extends BaseEntity {}

    private record TestView(Long id) {}

    private record CreateDTO(String name) {}

    private record UpdateDTO(String name) {}

    private static final class TestPageParam extends PageParam {}
}
