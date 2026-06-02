package com.xuejiai.aaf.framework.intelligent.team;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_team")
public class TeamEntity extends BaseEntity {

    @Column(name = "team_id", nullable = false, unique = true, length = 64)
    private String teamId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "collaboration_mode", nullable = false, length = 32)
    private String collaborationMode;

    @Column(name = "coordinator_assistant_id", length = 64)
    private String coordinatorAssistantId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "active";
}
