package com.xuejiai.aaf.framework.crud.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

class DefaultCrudViewMapperTest {

    private final DefaultCrudViewMapper mapper = new DefaultCrudViewMapper();

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Given 标量和关联批量数据 When 组装视图 Then 自动写入同名字段与关联字段")
    void should_map_scalar_and_relation_fields() {
        var entity = new TestEntity();
        entity.setId(10L);
        entity.setTitle("待办");
        var assignee = new ResourceRefDTO("system.user", 7L, "用户甲", null);
        var participants =
                List.of(
                        new ResourceRefDTO("system.user", 8L, "用户乙", null),
                        new ResourceRefDTO("system.user", 9L, "用户丙", null));
        var plan =
                new CrudViewPlan(
                        "detail",
                        Set.of("id", "title", "assignee", "participants"),
                        Map.of(
                                "assignee", Set.of("assignee"),
                                "participants", Set.of("participants")),
                        "defaultCrudViewMapper");
        var data =
                CrudViewData.of(
                        Map.of(
                                "assignee", Map.of(10L, assignee),
                                "participants", Map.of(10L, participants)));

        var view =
                (TestView)
                        mapper.toView(
                                entity,
                                (Class<Object>) (Class<?>) TestView.class,
                                plan,
                                data);

        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.title()).isEqualTo("待办");
        assertThat(view.assignee()).isEqualTo(assignee);
        assertThat(view.participants()).containsExactlyElementsOf(participants);
    }

    private static final class TestEntity extends BaseEntity {
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    private record TestView(
            Long id,
            String title,
            ResourceRefDTO assignee,
            List<ResourceRefDTO> participants) {}
}
