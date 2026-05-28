/**
 * 访问策略实体（ABAC 层）。
 *
 * <p>基于属性的动态条件策略，支持时间/IP/置信度等条件。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.system.role.policy;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_access_policy")
public class AccessPolicy extends BaseEntity {

    /** 策略名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 描述 */
    @Column(length = 512)
    private String description;

    /** 条件表达式（JSON 格式） */
    @Column(name = "condition_json", columnDefinition = "TEXT")
    private String conditionJson;

    /** 效果：ALLOW / DENY */
    @Column(nullable = false, length = 16)
    private String effect = "ALLOW";

    /** 优先级（数值越小优先级越高） */
    @Column(nullable = false)
    private Integer priority = 100;

    /** 目标资源类型 */
    @Column(name = "target_resource", length = 64)
    private String targetResource;

    /** 目标操作 */
    @Column(name = "target_action", length = 64)
    private String targetAction;

    /** 状态（0=禁用 1=启用） */
    @Column(nullable = false)
    private Integer status = 1;
}
