package com.xuejiai.aaf.module.system.task.domain;

import java.io.Serializable;

import com.xuejiai.aaf.framework.crud.relation.AssociationKind;
import com.xuejiai.aaf.framework.crud.relation.CrudAssociation;
import com.xuejiai.aaf.framework.crud.relation.RelationDefinition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 待办-参与人关联。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_todo_participant")
@IdClass(TodoParticipant.Id.class)
@CrudAssociation(
        sourceEntity = Todo.class,
        key = "participants",
        kind = AssociationKind.MANY_TO_MANY_JOIN,
        sourceProperty = "todoId",
        targetProperty = "userId",
        targetResource = "system.user",
        inputField = "participants",
        viewField = "participants",
        syncMode = RelationDefinition.SyncMode.REPLACE,
        maxCardinality = 100,
        additionalPolicyBean = "todoUserReferencePolicy")
public class TodoParticipant {

    /** 待办 ID。 */
    @jakarta.persistence.Id
    @Column(name = "todo_id", nullable = false)
    private Long todoId;

    /** 参与人用户 ID。 */
    @jakarta.persistence.Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 待办-参与人复合主键。 */
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        private Long todoId;
        private Long userId;
    }
}
