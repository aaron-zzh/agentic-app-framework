package com.xuejiai.aaf.module.ai.aigc.image.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.config.StorageWebConfig;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantAuthFilter;
import com.xuejiai.aaf.framework.logging.RequestMetricsFilter;
import com.xuejiai.aaf.framework.security.SecurityConfig;
import com.xuejiai.aaf.framework.security.apikey.ApiKeyAuthFilter;
import com.xuejiai.aaf.module.ai.aigc.image.service.GenerationTemplateService;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateVO;

/**
 * 参数模板接口单元测试。
 *
 * @author AaronZZH & Kiro
 */
@WebMvcTest(
        controllers = GenerationTemplateController.class,
        excludeFilters =
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            StorageWebConfig.class,
                            RequestMetricsFilter.class,
                            SecurityConfig.class,
                            ApiKeyAuthFilter.class,
                            AssistantAuthFilter.class
                        }))
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import(com.xuejiai.aaf.config.GlobalExceptionHandler.class)
class GenerationTemplateControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GenerationTemplateService templateService;

    private static GenerationTemplateVO sampleVO(Long id, String scope) {
        return new GenerationTemplateVO(
                id,
                "IMAGE",
                "写实摄影",
                "通用风格",
                "photorealistic, 8K",
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                0,
                scope);
    }

    @Test
    @DisplayName("GET /public?type=IMAGE&scope=PROJECT 返回项目级模板列表")
    @WithMockUser
    void should_return_public_templates_when_query_by_type_and_scope() throws Exception {
        var pageResult = new PageResult<>(List.of(sampleVO(1L, "PROJECT")), 1L);
        when(templateService.page(any())).thenReturn(pageResult);

        mockMvc.perform(
                        get("/api/aigc/templates/public")
                                .param("type", "IMAGE")
                                .param("scope", "PROJECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].scope").value("PROJECT"));
    }

    @Test
    @DisplayName("GET /{id} 访问无权限记录 返回 404")
    @WithMockUser
    void should_return_404_when_access_forbidden_template() throws Exception {
        when(templateService.getById(99L, null, "detail"))
                .thenThrow(new BusinessException(GlobalErrorCode.NOT_FOUND, "参数模板不存在"));

        mockMvc.perform(get("/api/aigc/templates/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("参数模板不存在"));
    }

    @Test
    @DisplayName("POST /{id}/use 增加使用计数 返回更新后 VO")
    @WithMockUser
    void should_increment_usage_when_use_template() throws Exception {
        var vo = sampleVO(1L, "GENERATION");
        when(templateService.incrementUsage(1L)).thenReturn(vo);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/aigc/templates/1/use"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }
}
