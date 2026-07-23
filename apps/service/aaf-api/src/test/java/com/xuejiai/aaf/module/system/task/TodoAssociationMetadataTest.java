package com.xuejiai.aaf.module.system.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.relation.AssociationKind;
import com.xuejiai.aaf.framework.crud.relation.CrudAssociation;
import com.xuejiai.aaf.framework.crud.relation.RelationDefinition;
import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.domain.TodoParticipant;

class TodoAssociationMetadataTest {

    @Test
    @DisplayName("Given assigneeId 字段 When 读取声明 Then 按约定生成固定用户引用")
    void should_declare_assignee_as_field_reference() throws NoSuchFieldException {
        var annotation = Todo.class.getDeclaredField("assigneeId").getAnnotation(CrudReference.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.key()).isEmpty();
        assertThat(annotation.idProperty()).isEmpty();
        assertThat(annotation.targetResource()).isEqualTo("system.user");
        assertThat(annotation.additionalPolicyBean()).isEqualTo("todoUserReferencePolicy");
    }

    @Test
    @DisplayName("Given sourceId 字段 When 读取声明 Then 使用 sourceEntity 组成多态引用")
    void should_declare_source_as_polymorphic_field_reference() throws NoSuchFieldException {
        var annotation = Todo.class.getDeclaredField("sourceId").getAnnotation(CrudReference.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.resourceProperty()).isEqualTo("sourceEntity");
        assertThat(annotation.additionalPolicyBean()).isEmpty();
    }

    @Test
    @DisplayName("Given TodoParticipant When 读取声明 Then 使用纯关联表 REPLACE 参与人")
    void should_declare_participants_as_many_to_many_join() {
        var annotation = TodoParticipant.class.getAnnotation(CrudAssociation.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.sourceEntity()).isEqualTo(Todo.class);
        assertThat(annotation.kind()).isEqualTo(AssociationKind.MANY_TO_MANY_JOIN);
        assertThat(annotation.syncMode()).isEqualTo(RelationDefinition.SyncMode.REPLACE);
        assertThat(annotation.sourceProperty()).isEqualTo("todoId");
        assertThat(annotation.targetProperty()).isEqualTo("userId");
        assertThat(annotation.maxCardinality()).isEqualTo(100);
    }
}
