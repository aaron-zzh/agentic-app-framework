package com.xuejiai.aaf.module.system.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.system.service.UserService;
import com.xuejiai.aaf.module.system.vo.UserCreateDTO;
import com.xuejiai.aaf.module.system.vo.UserPageDTO;
import com.xuejiai.aaf.module.system.vo.UserUpdateDTO;
import com.xuejiai.aaf.module.system.vo.UserVO;

/** 用户接口单元测试（@WebMvcTest 切片测试，不加载完整上下文）。 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserVO sampleUser = new UserVO(1L, "testuser", "测试", (short) 1, null, null);

    @Test
    @DisplayName("Given 合法请求 When POST /users Then 返回成功")
    @WithMockUser
    void should_create_user_when_valid_request() throws Exception {
        // 准备参数
        var request = new UserCreateDTO("testuser", "123456", "测试");

        // mock 方法
        when(userService.create(any())).thenReturn(sampleUser);

        // 调用 + 断言
        mockMvc.perform(
                        post("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    @DisplayName("Given 用户存在 When GET /users/{id} Then 返回用户详情")
    @WithMockUser
    void should_return_user_when_get_by_id() throws Exception {
        // mock 方法
        when(userService.getById(1L)).thenReturn(sampleUser);

        // 调用 + 断言
        mockMvc.perform(get("/api/system/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("Given 有用户数据 When GET /users?pageNo&pageSize Then 返回分页列表")
    @WithMockUser
    void should_return_page_when_list_users() throws Exception {
        // mock 方法
        var pageResult = new PageResult<>(List.of(sampleUser), 1L);
        when(userService.page(any(UserPageDTO.class))).thenReturn(pageResult);

        // 调用 + 断言
        mockMvc.perform(get("/api/system/users").param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].username").value("testuser"));
    }

    @Test
    @DisplayName("Given 合法请求 When PUT /users/{id} Then 返回更新后的用户")
    @WithMockUser
    void should_update_user_when_valid_request() throws Exception {
        // 准备参数
        var updated = new UserVO(1L, "testuser", "新昵称", (short) 1, null, null);

        // mock 方法
        when(userService.update(eq(1L), any())).thenReturn(updated);

        // 调用 + 断言
        mockMvc.perform(
                        put("/api/system/users/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new UserUpdateDTO("新昵称", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));
    }

    @Test
    @DisplayName("Given 用户存在 When DELETE /users/{id} Then 返回成功")
    @WithMockUser
    void should_delete_user_when_valid_id() throws Exception {
        // 调用 + 断言
        mockMvc.perform(delete("/api/system/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
