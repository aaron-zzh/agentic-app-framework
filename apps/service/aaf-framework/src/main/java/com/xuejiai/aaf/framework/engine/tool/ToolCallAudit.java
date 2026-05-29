package com.xuejiai.aaf.framework.engine.tool;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 工具调用审计记录。 */
@Getter
@Setter
@Entity
@Table(name = "tool_call_audit")
public class ToolCallAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "function_name", nullable = false, length = 128)
    private String functionName;

    @Column(name = "arguments", columnDefinition = "TEXT")
    private String arguments;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "output", columnDefinition = "TEXT")
    private String output;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
