package com.xuejiai.aaf.framework.intelligent.team;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 团队定义。
 *
 * <p>标注 {@link com.xuejiai.aaf.framework.org.OrgIgnore}：当前 {@code TeamOrchestrator#createTeam} 从未设置
 * org_id，运行时该表实际上是全局的。若未来团队需要按组织隔离，需先在创建入口补齐 org_id 写入， 再移除本标注（否则组织过滤会让已有团队查询静默返回空）。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_team")
@com.xuejiai.aaf.framework.org.OrgIgnore
public class TeamEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "collaboration_mode", nullable = false, length = 32)
    private String collaborationMode;

    @Column(name = "coordinator_assistant_id")
    private Long coordinatorAssistantId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "active";
}
