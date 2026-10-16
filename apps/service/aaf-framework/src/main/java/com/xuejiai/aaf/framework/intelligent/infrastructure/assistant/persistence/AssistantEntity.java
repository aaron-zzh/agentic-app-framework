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

    @Column(name = "source_system_key", length = 128)
    private String sourceSystemKey;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

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

    /** 助理绑定的默认知识库（ai_knowledge_base.id）；回答时自动参与检索，可为空。 */
    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    @Column(nullable = false, length = 16)
    private String status;
}
