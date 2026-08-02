package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeUsagePortTest {

    @Test
    @DisplayName("Given 相同代际阶段单元和调用标识 When 生成 usageKey Then 多次结果完全一致")
    void should_generate_stable_usage_key_for_same_invocation() {
        var runId = UUID.randomUUID();

        var first = KnowledgeUsagePort.usageKey(runId, "extraction", "chunk:1", "call-1");
        var second = KnowledgeUsagePort.usageKey(runId, "extraction", "chunk:1", "call-1");

        assertThat(first)
                .isEqualTo(second)
                .isEqualTo("knowledge:ingest:%s:extraction:chunk:1:call-1".formatted(runId));
    }
}
