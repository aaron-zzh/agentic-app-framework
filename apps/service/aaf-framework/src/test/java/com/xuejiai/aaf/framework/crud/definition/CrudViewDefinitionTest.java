package com.xuejiai.aaf.framework.crud.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

class CrudViewDefinitionTest {

    @Test
    @DisplayName("Given 输出包含引用字段 When 生成默认字段集 Then 仅 detail 默认展开引用")
    void should_only_expand_references_in_detail_by_default() {
        var types =
                new CrudResourceTypeContract<>(
                        TestEntity.class,
                        Void.class,
                        Void.class,
                        TestView.class,
                        PageParam.class);

        var view = CrudViewDefinition.forTypes(types);

        assertThat(view.fieldSets().get("detail"))
                .containsExactlyInAnyOrder("id", "title", "assignee", "participants");
        assertThat(view.fieldSets().get("list"))
                .containsExactlyInAnyOrder("id", "title");
        assertThat(view.fieldSets().get("picker"))
                .containsExactlyInAnyOrder("id", "title");
        assertThat(view.fieldSets().get("export"))
                .containsExactlyInAnyOrder("id", "title");
    }

    @Test
    @DisplayName("Given 默认视图 When 差量替换 list Then detail 仍保持全部输出字段")
    void should_override_one_field_set_and_keep_default_detail() {
        var types =
                new CrudResourceTypeContract<>(
                        TestEntity.class,
                        Void.class,
                        Void.class,
                        TestView.class,
                        PageParam.class);

        var view = CrudViewDefinition.forTypes(types).withFieldSet("list", Set.of("id"));

        assertThat(view.fieldSets().get("list")).containsExactly("id");
        assertThat(view.fieldSets().get("detail"))
                .containsExactlyInAnyOrder("id", "title", "assignee", "participants");
    }

    private static final class TestEntity extends BaseEntity {}

    private record TestView(
            Long id,
            String title,
            ResourceRefDTO assignee,
            List<ResourceRefDTO> participants) {}
}
