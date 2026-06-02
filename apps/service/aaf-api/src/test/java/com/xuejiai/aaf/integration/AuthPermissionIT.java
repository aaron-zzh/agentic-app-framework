package com.xuejiai.aaf.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;

/** 认证→权限→业务联调集成测试 验证：登录→获取Token→访问受保护接口→无权限拒绝 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
class AuthPermissionIT {

    @Autowired private MockMvc mockMvc;

    private static String token;

    @Test
    @Order(1)
    @DisplayName("Given 有效凭证 When 登录 Then 返回 JWT Token")
    void should_return_token_when_login_with_valid_credentials() throws Exception {
        var result =
                mockMvc.perform(
                                post("/api/system/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {"username":"admin","password":"admin123"}
                                """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.accessToken").exists())
                        .andReturn();

        // 保存 token 供后续测试使用
        token =
                com.jayway.jsonpath.JsonPath.read(
                        result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    @Order(2)
    @DisplayName("Given 有效 Token When 访问受保护接口 Then 返回 200")
    void should_access_protected_api_with_valid_token() throws Exception {
        mockMvc.perform(get("/api/system/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    @DisplayName("Given 无 Token When 访问受保护接口 Then 返回 401")
    void should_return_401_when_no_token() throws Exception {
        mockMvc.perform(get("/api/system/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("Given 无权限角色 When 访问管理接口 Then 返回 403")
    void should_return_403_when_no_permission() throws Exception {
        // 使用普通用户 token 访问管理员接口
        mockMvc.perform(get("/api/system/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
