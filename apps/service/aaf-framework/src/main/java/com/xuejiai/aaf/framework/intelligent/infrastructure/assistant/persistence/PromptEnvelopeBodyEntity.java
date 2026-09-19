package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelopeBody;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 与信封一一对应的完整 Prompt 正文存储。
 *
 * <p>与 {@code ai_prompt_envelope} 分表：信封读取（重试检测、恢复、审计列表）不加载正文，正文只在显式调试查询时读取。 当前按人类决策（AAF-113
 * #11328）明文长期保存，无加密、无访问审计、无到期删除。
 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_prompt_envelope_body",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"tenant_id", "execution_id", "envelope_seq"}))
public class PromptEnvelopeBodyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "envelope_id", nullable = false, length = 64)
    private String envelopeId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;

    @Column(name = "envelope_seq", nullable = false)
    private Integer envelopeSeq;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body_payload", nullable = false, columnDefinition = "jsonb")
    private PromptEnvelopeBody body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
