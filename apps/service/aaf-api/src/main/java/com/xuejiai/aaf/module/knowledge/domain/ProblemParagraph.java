package com.xuejiai.aaf.module.knowledge.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 问题-段落关联——一个问题可关联多个段落作为答案来源。 */
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

    @Column(nullable = false)
    private Long segmentId;
}
