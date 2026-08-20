package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** ai_assistant 当前定义；不提供运行时版本切换。 */
@Getter
@Setter
@Entity(name = "AiAssistant")
@Table(name = "ai_assistant")
public class AssistantEntity extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String code;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "persona_id", nullable = false)
    private Long personaId;

    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "memory_strategy", nullable = false, length = 32)
    private String memoryStrategy;

    @Column(name = "skill_ids", nullable = false, columnDefinition = "TEXT")
    private String skillIds;

    @Column(name = "tool_whitelist", nullable = false, columnDefinition = "TEXT")
    private String toolWhitelist;

    @Column(nullable = false, length = 16)
    private String status;
}
