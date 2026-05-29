package com.xuejiai.aaf.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 知识库→工作流跨模块集成测试
 * 验证：创建知识库→导入文档→触发工作流
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
@WithMockUser(roles = "ADMIN")
class KnowledgeWorkflowIT {

    @Autowired
    private MockMvc mockMvc;

    private static Long knowledgeBaseId;

    @Test
    @Order(1)
    @DisplayName("Given 管理员 When 创建知识库 Then 返回知识库 ID")
    void should_create_knowledge_base() throws Exception {
        var result = mockMvc.perform(post("/api/knowledge/bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"测试知识库","description":"集成测试用"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        knowledgeBaseId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    @Test
    @Order(2)
    @DisplayName("Given 知识库已创建 When 导入文档 Then 返回成功")
    void should_import_document_to_knowledge_base() throws Exception {
        var file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "测试文档内容".getBytes());

        mockMvc.perform(multipart("/api/knowledge/bases/{id}/documents", knowledgeBaseId)
                        .file(file))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    @DisplayName("Given 文档已导入 When 触发工作流 Then 工作流启动成功")
    void should_trigger_workflow_after_document_import() throws Exception {
        mockMvc.perform(post("/api/workflow/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledgeBaseId":%d,"workflowKey":"document-process"}
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instanceId").exists());
    }
}
