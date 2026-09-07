package com.xuejiai.aaf.module.ai.assistant.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.engine.knowledge.importer.DocumentSection;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImportResult;
import com.xuejiai.aaf.module.ai.assistant.document.DocumentTokenBudgetSplitter.DocumentTokenBudgetLimits;

class DocumentTokenBudgetSplitterTest {

    @Test
    @DisplayName("Given 段落总长度未超预算 When 裁剪 Then 返回完整内容且未标记截断")
    void should_return_full_content_when_within_budget() {
        // 准备参数
        var splitter = new DocumentTokenBudgetSplitter(new DocumentTokenBudgetLimits(1000, 500));
        var result =
                new ImportResult(
                        List.of(
                                new DocumentSection("第一段", 1, Map.of()),
                                new DocumentSection("第二段", 0, Map.of())),
                        "标题",
                        6);

        // 调用
        var split = splitter.split(result, "fileKey123", "hash123", "PlainTextImporter");

        // 断言
        assertThat(split.truncated()).isFalse();
        assertThat(split.content()).isEqualTo("第一段\n\n第二段");
        assertThat(split.provenance().resourceId()).isEqualTo("fileKey123");
        assertThat(split.provenance().contentHash()).isEqualTo("hash123");
        assertThat(split.provenance().importerName()).isEqualTo("PlainTextImporter");
        assertThat(split.provenance().truncated()).isFalse();
    }

    @Test
    @DisplayName("Given 段落总长度超过预算 When 裁剪 Then 显式标记截断")
    void should_mark_truncated_when_exceeding_budget() {
        // 准备参数：预算仅够容纳第一段
        var splitter = new DocumentTokenBudgetSplitter(new DocumentTokenBudgetLimits(3, 500));
        var result =
                new ImportResult(
                        List.of(
                                new DocumentSection("段一", 0, Map.of()),
                                new DocumentSection("段二", 0, Map.of())),
                        "标题",
                        4);

        // 调用
        var split = splitter.split(result, "fileKey", "hash", "PlainTextImporter");

        // 断言
        assertThat(split.truncated()).isTrue();
        assertThat(split.provenance().truncated()).isTrue();
        assertThat(split.provenance().originalCharacters()).isEqualTo(4);
    }

    @Test
    @DisplayName("Given 单段超过单段上限 When 裁剪 Then 该段被截断到上限长度")
    void should_clip_single_section_exceeding_section_limit() {
        // 准备参数：单段上限为 2 字符，段落实际 4 字符
        var splitter = new DocumentTokenBudgetSplitter(new DocumentTokenBudgetLimits(1000, 2));
        var result = new ImportResult(List.of(new DocumentSection("超长段落", 0, Map.of())), "标题", 4);

        // 调用
        var split = splitter.split(result, "fileKey", "hash", "PlainTextImporter");

        // 断言
        assertThat(split.content()).isEqualTo("超长");
    }

    @Test
    @DisplayName("Given provenance When 生成日志摘要 Then 包含关键字段")
    void should_generate_log_summary_with_key_fields() {
        // 准备参数
        var splitter = new DocumentTokenBudgetSplitter(new DocumentTokenBudgetLimits(1000, 500));
        var result = new ImportResult(List.of(new DocumentSection("内容", 0, Map.of())), "标题", 2);

        // 调用
        var split = splitter.split(result, "fileKey", "hash", "PlainTextImporter");
        var summary = split.provenance().toLogSummary();

        // 断言
        assertThat(summary).contains("fileKey").contains("hash").contains("PlainTextImporter");
    }
}
