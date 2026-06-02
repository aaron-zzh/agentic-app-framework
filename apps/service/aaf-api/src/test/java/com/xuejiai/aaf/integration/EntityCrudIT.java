package com.xuejiai.aaf.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** 通用 CRUD 全链路集成测试 验证：创建实体→查询→更新→删除→回收站恢复 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
@WithMockUser(roles = "ADMIN")
class EntityCrudIT {

    @Autowired private MockMvc mockMvc;

    private static Long entityId;

    @Test
    @Order(1)
    @DisplayName("Given 有效数据 When 创建实体 Then 返回实体 ID")
    void should_create_entity() throws Exception {
        var result =
                mockMvc.perform(
                                post("/api/system/depts")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {"name":"集成测试部门","sort":99}
                                """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.id").exists())
                        .andReturn();

        entityId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        result.getResponse().getContentAsString(), "$.data.id"))
                        .longValue();
    }

    @Test
    @Order(2)
    @DisplayName("Given 实体已创建 When 按 ID 查询 Then 返回实体详情")
    void should_query_entity_by_id() throws Exception {
        mockMvc.perform(get("/api/system/depts/{id}", entityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("集成测试部门"));
    }

    @Test
    @Order(3)
    @DisplayName("Given 实体已创建 When 更新名称 Then 返回更新后数据")
    void should_update_entity() throws Exception {
        mockMvc.perform(
                        put("/api/system/depts/{id}", entityId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"name":"更新后部门","sort":88}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("更新后部门"));
    }

    @Test
    @Order(4)
    @DisplayName("Given 实体已更新 When 删除 Then 返回成功")
    void should_delete_entity() throws Exception {
        mockMvc.perform(delete("/api/system/depts/{id}", entityId)).andExpect(status().isOk());
    }

    @Test
    @Order(5)
    @DisplayName("Given 实体已删除 When 从回收站恢复 Then 实体可再次查询")
    void should_restore_entity_from_trash() throws Exception {
        // 恢复
        mockMvc.perform(post("/api/system/depts/{id}/restore", entityId))
                .andExpect(status().isOk());

        // 验证恢复后可查询
        mockMvc.perform(get("/api/system/depts/{id}", entityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("更新后部门"));
    }
}
