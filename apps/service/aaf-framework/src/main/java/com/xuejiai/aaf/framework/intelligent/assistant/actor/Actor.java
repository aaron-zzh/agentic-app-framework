package com.xuejiai.aaf.framework.intelligent.assistant.actor;

import jakarta.persistence.*;

import com.xuejiai.aaf.common.model.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * Actor（人格载体）：定义助理的人格、角色扮演和基础系统提示词。
 * 可复用——同一个 Actor 可绑定不同 Role 组成多个 Assistant。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_actor")
public class Actor extends BaseEntity {

    /** Actor 唯一标识 */
    @Column(nullable = false, unique = true, length = 64)
    private String actorId;

    /** 显示名称（如"客服小美"） */
    @Column(nullable = false, length = 128)
    private String name;

    /** 人格描述（性格/语气/风格） */
    @Column(columnDefinition = "TEXT")
    private String persona;

    /** 基础系统提示词 */
    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    /** 头像 URL */
    @Column(length = 512)
    private String avatarUrl;

    /** 状态：active / inactive */
    @Column(nullable = false, length = 16)
    private String status = "active";
}
