package com.xuejiai.aaf.framework.intelligent.team;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_team_task")
public class TeamTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false, length = 64)
    private String teamId;

    @Column(name = "task_id", nullable = false, unique = true, length = 64)
    private String taskId;

    @Column(name = "parent_task_id", length = 64)
    private String parentTaskId;

    @Column(name = "assignee_id", length = 64)
    private String assigneeId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "required_capability", length = 128)
    private String requiredCapability;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "dependencies")
    private String dependencies;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Column(name = "progress", nullable = false)
    private Integer progress = 0;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
