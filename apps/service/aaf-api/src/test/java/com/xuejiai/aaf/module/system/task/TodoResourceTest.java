package com.xuejiai.aaf.module.system.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.vo.TodoVO;

class TodoResourceTest {

    @Test
    @DisplayName("Given Todo 默认资源契约 When 读取定义 Then 类型和能力仅体现业务差量")
    void should_derive_types_and_apply_capability_differences() {
        var definition = TodoResource.DEFINITION;

        assertThat(definition.types().entityType()).isEqualTo(Todo.class);
        assertThat(definition.types().viewType()).isEqualTo(TodoVO.class);
        assertThat(definition.capabilities().operations())
                .contains(
                        CrudOperation.CREATE,
                        CrudOperation.UPDATE,
                        CrudOperation.DELETE,
                        CrudOperation.EXPORT)
                .doesNotContain(CrudOperation.IMPORT, CrudOperation.RESTORE, CrudOperation.ARCHIVE);
    }

    @Test
    @DisplayName("Given Todo 视图差量 When 读取字段集 Then detail 保持默认全量并覆盖其他字段集")
    void should_keep_default_detail_and_customize_other_field_sets() {
        var fieldSets = TodoResource.DEFINITION.view().fieldSets();

        assertThat(fieldSets.get("detail"))
                .containsExactlyInAnyOrderElementsOf(
                        com.xuejiai.aaf.framework.crud.definition.CrudViewDefinition.forTypes(
                                        TodoResource.DEFINITION.types())
                                .fieldSets()
                                .get("detail"));
        assertThat(fieldSets.get("picker")).containsExactlyInAnyOrder("id", "title");
        assertThat(fieldSets.get("list")).contains("assignee", "participants");
        assertThat(fieldSets.get("export")).contains("source");
    }

    @Test
    @DisplayName("Given Todo 未声明字段能力表 When 读取定义 Then 从查询变更和视图契约推导")
    void should_infer_field_capabilities() {
        var capabilities = TodoResource.DEFINITION.fieldCapabilities();

        assertThat(capabilities).containsKeys("id", "title", "assigneeId", "participants");
        assertThat(capabilities.get("title")).isNotEmpty();
    }
}
