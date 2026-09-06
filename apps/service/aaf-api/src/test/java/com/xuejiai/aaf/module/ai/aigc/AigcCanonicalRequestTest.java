package com.xuejiai.aaf.module.ai.aigc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AigcCanonicalRequestTest {

    @Test
    @DisplayName("Given 业务字段相同 When expectedProjectVersion 变化 Then canonical hash 不同")
    void should_change_hash_when_cas_field_changes() {
        // 准备参数
        var business = Map.<String, Object>of("projectId", 1L, "title", "作品");

        // 调用
        var first =
                AigcCanonicalRequest.of(
                                "work.publish", business, Map.of("expectedProjectVersion", 3))
                        .sha256();
        var second =
                AigcCanonicalRequest.of(
                                "work.publish", business, Map.of("expectedProjectVersion", 4))
                        .sha256();

        // 断言
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("Given 嵌套 Map 插入顺序不同 When canonical hash Then 摘要一致")
    void should_keep_hash_when_nested_map_order_changes() {
        // 准备参数
        var left = new LinkedHashMap<String, Object>();
        left.put("b", 2);
        left.put("a", 1);
        var right = new LinkedHashMap<String, Object>();
        right.put("a", 1);
        right.put("b", 2);

        // 调用 + 断言
        assertThat(
                        AigcCanonicalRequest.of(
                                        "execution.submit",
                                        Map.of("arguments", left),
                                        Map.of("expectedGraphRevision", 8L))
                                .sha256())
                .isEqualTo(
                        AigcCanonicalRequest.of(
                                        "execution.submit",
                                        Map.of("arguments", right),
                                        Map.of("expectedGraphRevision", 8L))
                                .sha256());
    }
}
