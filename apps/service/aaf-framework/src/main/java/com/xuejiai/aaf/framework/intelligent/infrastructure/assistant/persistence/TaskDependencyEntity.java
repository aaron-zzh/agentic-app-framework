package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "ai_task_dependency",
        uniqueConstraints =
                @UniqueConstraint(
                        columnNames = {
                            "org_id",
                            "task_id",
                            "plan_id",
                            "plan_revision",
                            "predecessor_node_id",
                            "successor_node_id"
                        }))
public class TaskDependencyEntity extends AssistantRuntimeEntity {

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "plan_id", nullable = false, length = 128)
    private String planId;

    @Column(name = "plan_revision", nullable = false)
    private Integer planRevision;

    @Column(name = "predecessor_node_id", nullable = false, length = 128)
    private String predecessorNodeId;

    @Column(name = "successor_node_id", nullable = false, length = 128)
    private String successorNodeId;

    @Column(name = "dependency_type", nullable = false, length = 16)
    private String dependencyType;
}
