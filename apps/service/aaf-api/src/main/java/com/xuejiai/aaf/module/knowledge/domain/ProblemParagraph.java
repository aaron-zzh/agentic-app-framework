package com.xuejiai.aaf.module.knowledge.domain;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 问题-分块关联——一个问题可关联多个知识分块作为答案来源。 */
@Getter
@Setter
@Entity
@Table(name = "ai_knowledge_problem_paragraph")
public class ProblemParagraph {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long problemId;

    /** 关联分块的 ai_knowledge_chunk.stable_id */
    @Column(nullable = false)
    private UUID chunkId;
}
