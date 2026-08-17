package com.xuejiai.aaf.framework.engine.skill;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 不可变 Skill 执行版本，正文仅由 content 表达。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_skill_version",
        indexes =
                @Index(name = "idx_skill_version_current", columnList = "skill_id,status,version"))
public class SkillVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", columnDefinition = "JSONB")
    private String inputSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", columnDefinition = "JSONB")
    private String outputSchema;

    @Column(name = "output_contract", columnDefinition = "TEXT")
    private String outputContract;

    @Column(name = "tool_access_mode", nullable = false, length = 16)
    private String toolAccessMode = "RESTRICT";

    @Column(name = "change_summary", length = 512)
    private String changeSummary;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "authored_by")
    private Long authoredBy;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
