package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.StoredChunk;

class FocusExtractionContextAssemblerTest {

    private final FocusExtractionContextAssembler assembler = new FocusExtractionContextAssembler();

    @Test
    @DisplayName("Given 焦点块超过预算 When 组装上下文 Then 拒绝截断焦点证据")
    void should_reject_when_focus_exceeds_budget() {
        var chunks = List.of(chunk("不可截断的焦点证据"));

        assertThatThrownBy(() -> assembler.assemble(chunks, 0, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("焦点块超过");
    }

    @Test
    @DisplayName("Given 焦点开头存在指代风险 When 组装上下文 Then 前邻只保留后缀")
    void should_take_previous_suffix_when_previous_boundary_risk_exists() {
        var chunks = List.of(chunk("前邻完整内容-结尾"), chunk("该方案已经完整。"));

        var context = assembler.assemble(chunks, 1, "该方案已经完整。".length() + 4);

        assertThat(context.previousChunks())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.content()).isEqualTo("容-结尾");
                            assertThat(item.truncated()).isTrue();
                        });
        assertThat(context.nextChunks()).isEmpty();
    }

    @Test
    @DisplayName("Given 焦点结尾存在未完句风险 When 组装上下文 Then 后邻只保留前缀")
    void should_take_next_prefix_when_next_boundary_risk_exists() {
        var chunks = List.of(chunk("完整前句。"), chunk("方案仍需"), chunk("后邻开头-完整内容"));

        var context = assembler.assemble(chunks, 1, "方案仍需".length() + 4);

        assertThat(context.previousChunks()).isEmpty();
        assertThat(context.nextChunks())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.content()).isEqualTo("后邻开头");
                            assertThat(item.truncated()).isTrue();
                        });
    }

    @Test
    @DisplayName("Given 焦点边界完整 When 组装上下文 Then 不携带任何邻块")
    void should_not_include_neighbors_when_boundary_is_complete() {
        var chunks = List.of(chunk("前邻"), chunk("实体关系完整。"), chunk("后邻"));

        var context = assembler.assemble(chunks, 1, 100);

        assertThat(context.previousChunks()).isEmpty();
        assertThat(context.nextChunks()).isEmpty();
    }

    private StoredChunk chunk(String content) {
        return new StoredChunk(UUID.randomUUID(), 0, content, "hash", 1, Map.of());
    }
}
