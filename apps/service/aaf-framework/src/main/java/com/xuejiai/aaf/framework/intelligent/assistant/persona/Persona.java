package com.xuejiai.aaf.framework.intelligent.assistant.persona;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Persona（人格载体）：定义助理的人格、角色扮演和基础系统提示词。 可复用——同一个 Persona 可绑定不同 Role 组成多个 Assistant。 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona")
public class Persona extends BaseEntity {

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

    /**
     * 所有者用户 ID。 NULL = 系统公共模板（所有用户可选）；有值 = 用户私有人格（仅所属用户可用）。 用户基于公共模板定制时，复制一条并设置 ownerId，Assistant
     * 改指向新记录。
     */
    @Column(name = "owner_id")
    private Long ownerId;
}
