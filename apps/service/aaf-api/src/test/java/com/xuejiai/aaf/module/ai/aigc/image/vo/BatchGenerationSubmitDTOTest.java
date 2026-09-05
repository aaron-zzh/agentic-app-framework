package com.xuejiai.aaf.module.ai.aigc.image.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchGenerationSubmitDTOTest {

    @Test
    @DisplayName("Given 公共批量生成 DTO When 检查请求契约 Then 不暴露 projectId")
    void should_not_expose_project_semantics() {
        // 调用
        var componentNames =
                Arrays.stream(BatchGenerationSubmitDTO.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList();

        // 断言
        assertThat(componentNames)
                .containsExactly("prompts", "model", "width", "height")
                .doesNotContain("projectId");
    }
}
