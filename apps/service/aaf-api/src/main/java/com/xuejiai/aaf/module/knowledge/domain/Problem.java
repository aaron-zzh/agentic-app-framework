package com.xuejiai.aaf.module.knowledge.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** QA 问题——手动维护的问答对，检索时优先匹配。 */
@Getter
@Setter
@Entity
@Table(name = "knowledge_problem")
public class Problem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long knowledgeBaseId;

    /** 问题内容 */
    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "is_active")
    private Boolean active = true;
}
