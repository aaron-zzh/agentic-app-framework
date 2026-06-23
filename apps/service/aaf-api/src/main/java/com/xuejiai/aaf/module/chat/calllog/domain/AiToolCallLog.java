package com.xuejiai.aaf.module.chat.calllog.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 工具调用日志——记录每次 onActing 中的单个工具调用。
 *
 * <p>一次 onActing 可能包含多个工具调用，每个对应一行。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_tool_call_log")
@SQLDelete(
        sql =
                "UPDATE ai_tool_call_log SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AiToolCallLog extends BaseEntity {

    /** 所属会话 ID */
    @Column(name = "conversation_id")
    private Long conversationId;

    /** 所属用户 ID */
    @Column(name = "user_id")
    private Long userId;

    /** AG-UI threadId */
    @Column(name = "thread_id", length = 64)
    private String threadId;

    /** 工具调用 ID（来自 ToolUseBlock.getId()） */
    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    /** 工具名称（来自 ToolUseBlock.getName()） */
    @Column(name = "tool_name", length = 128)
    private String toolName;

    /** 工具来源：LOCAL/MCP */
    @Column(name = "tool_source", length = 32)
    private String toolSource = "LOCAL";

    /** 工具调用入参（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_input", columnDefinition = "jsonb")
    private String toolInput;

    /** 执行结果 */
    @Column(name = "result", columnDefinition = "text")
    private String result;

    /** 错误信息 */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** 执行耗时（毫秒） */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 工具调用状态：STARTED / COMPLETED / ERROR */
    @Column(name = "status", length = 16)
    private String status = "STARTED";

    /** 风险等级：LOW/MEDIUM/HIGH */
    @Column(name = "risk_level", length = 16)
    private String riskLevel = "LOW";
}
