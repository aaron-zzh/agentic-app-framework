package com.xuejiai.aaf.framework.intelligent.team;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_team_member")
public class TeamMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "assistant_id", nullable = false)
    private Long assistantId;

    @Column(name = "role", nullable = false, length = 32)
    private String role = "member";

    @Column(name = "capabilities")
    private String capabilities;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
