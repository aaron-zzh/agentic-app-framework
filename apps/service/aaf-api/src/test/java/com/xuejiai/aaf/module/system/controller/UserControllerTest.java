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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.module.system.service.UserService;
import com.xuejiai.aaf.module.system.vo.UserCreateReqVO;
import com.xuejiai.aaf.module.system.vo.UserRespVO;
import com.xuejiai.aaf.module.system.vo.UserUpdateReqVO;

/** 用户接口单元测试（@WebMvcTest 切片测试，不加载完整上下文）。 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRespVO sampleUser = new UserRespVO(1L, "testuser", "测试", (short) 1, null, null);

    @Test
    @WithMockUser
    void create_成功() throws Exception {
        var request = new UserCreateReqVO("testuser", "123456", "测试");
        when(userService.create(any())).thenReturn(sampleUser);

        mockMvc.perform(
                        post("/api/system/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    @WithMockUser
    void get_成功() throws Exception {
        when(userService.getById(1L)).thenReturn(sampleUser);

        mockMvc.perform(get("/api/system/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @WithMockUser
    void page_成功() throws Exception {
        var pageResult = new PageResult<>(List.of(sampleUser), 1L);
        when(userService.page(any(PageParam.class))).thenReturn(pageResult);

        mockMvc.perform(get("/api/system/users").param("pageNo", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].username").value("testuser"));
    }

    @Test
    @WithMockUser
    void update_成功() throws Exception {
        var updated = new UserRespVO(1L, "testuser", "新昵称", (short) 1, null, null);
        when(userService.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(
                        put("/api/system/users/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new UserUpdateReqVO("新昵称", (short) 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));
    }

    @Test
    @WithMockUser
    void delete_成功() throws Exception {
        mockMvc.perform(delete("/api/system/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
