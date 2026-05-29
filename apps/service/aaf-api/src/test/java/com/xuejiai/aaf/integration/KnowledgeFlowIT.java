package com.xuejiai.aaf.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeBaseService;

/** 知识库核心流程集成测试。 */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeFlowIT {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Test
    void 创建知识库_导入文档_检索() {
        // 创建知识库
        var kb = knowledgeBaseService.create("IT测试知识库", "集成测试用", 1L);
        assertThat(kb).isNotNull();
        assertThat(kb.getName()).isEqualTo("IT测试知识库");

        // 导入文本
        knowledgeBaseService.importText(kb.getId(), "AAF 是生产级 AI 原生多智能体应用开发框架", "intro.md");

        // 检索
        var results = knowledgeBaseService.search(kb.getId(), "什么是 AAF", 5);
        assertThat(results).isNotEmpty();
    }
}
