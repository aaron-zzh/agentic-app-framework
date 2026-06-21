package com.xuejiai.aaf.module.ai.aigc.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.ai.aigc.image.domain.GenerationTemplate;
import com.xuejiai.aaf.module.ai.aigc.image.repository.GenerationTemplateRepository;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplatePageDTO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/**
 * 参数模板服务单元测试。
 *
 * @author AaronZZH & Kiro
 */
class GenerationTemplateServiceTest extends BaseMockitoUnitTest {

    @Mock private GenerationTemplateRepository templateRepository;
    @InjectMocks private GenerationTemplateService templateService;

    private static GenerationTemplate template(Long id, Long userId, boolean isPublic) {
        var t = new GenerationTemplate();
        t.setId(id);
        t.setName("测试模板");
        t.setType("IMAGE_GEN");
        t.setCategory("通用风格");
        t.setPrompt("photorealistic");
        t.setUserId(userId);
        t.setIsPublic(isPublic);
        t.setScope("GENERATION");
        t.setUsageCount(0);
        return t;
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Given 存在模板 When page(type+scope) Then 调用 repository 并返回结果")
    void should_return_page_when_query_by_type_and_scope() {
        var query = new GenerationTemplatePageDTO();
        query.setType("IMAGE_GEN");
        query.setScope("PROJECT");
        query.setIsPublic(true);
        query.setPageNo(1);
        query.setPageSize(10);

        var t = template(1L, 1L, true);
        t.setScope("PROJECT");
        when(templateRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t)));

        var result = templateService.page(query);

        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).scope()).isEqualTo("PROJECT");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Given 模板存在且可访问 When getById Then 返回 VO")
    void should_return_vo_when_template_accessible() {
        var t = template(1L, 100L, false);
        when(templateRepository.findOne(any(Specification.class))).thenReturn(Optional.of(t));

        var vo = templateService.getById(1L);

        assertThat(vo.id()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Given 模板不可见（数据权限过滤后为空）When getById Then 抛 404")
    void should_throw_404_when_template_not_visible() {
        // 数据权限规则过滤后 findOne 返回 empty（模拟无权限或不存在）
        when(templateRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.getById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数模板不存在");
    }

    @Test
    @DisplayName("Given 模板存在 When incrementUsage Then usageCount +1 并保存")
    void should_increment_usage_count_when_use_template() {
        var t = template(1L, 1L, true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var vo = templateService.incrementUsage(1L);

        assertThat(vo.usageCount()).isEqualTo(1);
        verify(templateRepository).save(t);
    }
}
