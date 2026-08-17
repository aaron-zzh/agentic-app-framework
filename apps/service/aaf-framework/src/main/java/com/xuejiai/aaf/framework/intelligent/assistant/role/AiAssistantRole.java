package com.xuejiai.aaf.framework.intelligent.assistant.role;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 助理-角色关联（M:N）。
 *
 * <p>角色为可复用定义，可跨助理挂载；一个助理可挂载多个角色。{@code isDefault} 是默认角色的唯一真理源， 每个 Assistant 必须且只能有一个未删除的默认关联。
 *
 * <p>标注 {@link OrgIgnore}：纯 M:N 关联表，无自己的组织语义，归属由 assistant/role 各自决定，{@code org_id} 恒为 NULL。
 */
@Getter
@Setter
@Entity(name = "AiAssistantRole")
@Table(
        name = "ai_assistant_role",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_ai_assistant_role",
                        columnNames = {"assistant_id", "role_id"}),
        indexes = {
            @Index(name = "idx_ai_assistant_role_assistant", columnList = "assistant_id"),
            @Index(name = "idx_ai_assistant_role_role", columnList = "role_id")
        })
@com.xuejiai.aaf.framework.org.OrgIgnore
public class AiAssistantRole extends BaseEntity {

    /** 关联的助理 ID */
    @Column(name = "assistant_id", nullable = false)
    private Long assistantId;

    /** 关联的角色 ID */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /** 是否为该助理的默认角色 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    /** 排序值（越小越靠前） */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    /** 是否参与当前 Assistant 的 Role Scope。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
