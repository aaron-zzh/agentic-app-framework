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
 * LLM 调用日志——记录每次 onModelCall 的 token 消耗与耗时。
 *
 * <p>与 conversation_message 同会话关联，但粒度更细：每次 LLM API 调用对应一行。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_llm_call_log")
@SQLDelete(
        sql =
                "UPDATE ai_llm_call_log SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AiLlmCallLog extends BaseEntity {

    /** 所属会话 ID（可为 null，当上下文未携带 conversationId 时） */
    @Column(name = "conversation_id")
    private Long conversationId;

    /** 所属用户 ID */
    @Column(name = "user_id")
    private Long userId;

    /** 所属助理 ID */
    @Column(name = "assistant_id")
    private Long assistantId;

    /** AG-UI threadId */
    @Column(name = "thread_id", length = 64)
    private String threadId;

    /** 模型名称（来自 ModelCallInput.model().getClass().getSimpleName()，或 replyId 关联） */
    @Column(name = "model_name", length = 100)
    private String modelName;

    /** 模型提供商 */
    @Column(name = "provider", length = 32)
    private String provider;

    /** 本次调用的 replyId（来自 ModelCallEndEvent） */
    @Column(name = "reply_id", length = 64)
    private String replyId;

    /** 调用意图：CHAT/PLANNING/SUMMARIZE/EMBEDDING 等 */
    @Column(name = "call_purpose", length = 64)
    private String callPurpose;

    /** 系统提示词 */
    @Column(name = "system_prompt", columnDefinition = "text")
    private String systemPrompt;

    /** 完整消息列表（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "messages", columnDefinition = "jsonb")
    private String messages;

    /** 模型输出文本 */
    @Column(name = "response", columnDefinition = "text")
    private String response;

    /** 结束原因：stop/tool_calls/length/content_filter */
    @Column(name = "finish_reason", length = 32)
    private String finishReason;

    /** 输入 token 数 */
    @Column(name = "input_tokens")
    private Integer inputTokens;

    /** 输出 token 数 */
    @Column(name = "output_tokens")
    private Integer outputTokens;

    /** 总 token 数 */
    @Column(name = "total_tokens")
    private Integer totalTokens;

    /** LLM 响应耗时（秒） */
    @Column(name = "duration_seconds")
    private Double durationSeconds;

    /** 错误码 */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** 错误信息 */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** 链路追踪 ID */
    @Column(name = "trace_id", length = 64)
    private String traceId;
}
