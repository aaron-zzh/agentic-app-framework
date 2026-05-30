package com.xuejiai.aaf.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xuejiai.aaf.module.knowledge.service.KnowledgeBaseService;
import com.xuejiai.aaf.module.knowledge.vo.CreateKnowledgeBaseRequest;

/** 知识库核心流程集成测试。 */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeFlowIT {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Test
    void 创建知识库() {
        var req = new CreateKnowledgeBaseRequest(
                "IT测试知识库", "集成测试用", null, null, null, null);
        var kb = knowledgeBaseService.create(req);
        assertThat(kb).isNotNull();
        assertThat(kb.name()).isEqualTo("IT测试知识库");
    }
}
