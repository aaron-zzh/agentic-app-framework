package com.xuejiai.aaf.module.ai.prompt.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplate;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplateCompiler;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplateRepository;
import com.xuejiai.aaf.framework.engine.prompt.PromptVisibility;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUpdateDTO;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 统一提示词资产服务单元测试。 */
class PromptTemplateAssetServiceTest extends BaseMockitoUnitTest {

    @Mock private PromptTemplateRepository repository;
    @Mock private PromptTemplateCompiler compiler;
    @Mock private OperatorContext operatorContext;
    @InjectMocks private PromptTemplateAssetService service;

    @Test
    @DisplayName("Given 公开模板非当前用户创建 When 更新 Then 按不存在拒绝写入")
    void should_reject_update_when_public_template_owned_by_another_user() {
        var template = new PromptTemplate();
        template.setOwnerId(7L);
        template.setVisibility(PromptVisibility.PUBLIC);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(8L));
        var request =
                new PromptTemplateUpdateDTO(
                        "新名称",
                        null,
                        null,
                        Patch.<String>absent(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> service.beforeUpdate(template, request))
                .isInstanceOf(BusinessException.class);
    }
}
