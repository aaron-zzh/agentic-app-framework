/**
 * Token 用量记录实体。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.token;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 每次 LLM 调用的 Token 消耗记录。 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_token_usage",
        indexes = {
            @Index(columnList = "userId,createdAt"),
            @Index(columnList = "conversationId"),
            @Index(columnList = "modelId,createdAt")
        })
public class TokenUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID */
    @Column(nullable = false)
    private Long userId;

    /** 会话 ID */
    @Column(length = 64)
    private String conversationId;

    /** 模型 ID */
    @Column(nullable = false, length = 64)
    private String modelId;

    /** 输入 Token 数 */
    @Column(nullable = false)
    private Long promptTokens;

    /** 输出 Token 数 */
    @Column(nullable = false)
    private Long completionTokens;

    /** 总 Token 数 */
    @Column(nullable = false)
    private Long totalTokens;

    /** 记录时间 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.totalTokens = this.promptTokens + this.completionTokens;
    }
}
