package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.util.JsonUtils;

class AigcConfigurationPatchDecoderTest {

    @Test
    @DisplayName("Given 动态嵌套配置 When 解码 objectMap Then 保留列表顺序与嵌套字段")
    void should_preserve_dynamic_configuration_shape() {
        // 准备参数
        var node =
                JsonUtils.readTree(
                        """
                        {
                          "steps": ["draft", "review", "publish"],
                          "policy": {"reviewRequired": true, "maxRetry": 2}
                        }
                        """);

        // 调用
        var result = AigcConfigurationPatchDecoder.objectMap(node);

        // 断言
        assertThat(result.get("steps")).isEqualTo(List.of("draft", "review", "publish"));
        assertThat(result.get("policy")).isEqualTo(Map.of("reviewRequired", true, "maxRetry", 2));
    }

    @Test
    @DisplayName("Given 动态配置整数收到字符串 When 解码 Then fail-fast 不做兼容转换")
    void should_reject_string_when_integer_is_required() {
        // 准备参数
        var node = JsonUtils.readTree("\"2\"");

        // 调用 + 断言
        assertThatThrownBy(() -> AigcConfigurationPatchDecoder.integer(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("整数");
    }
}
