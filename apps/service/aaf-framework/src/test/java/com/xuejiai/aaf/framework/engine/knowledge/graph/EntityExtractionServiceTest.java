package com.xuejiai.aaf.framework.engine.knowledge.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.FocusExtractionContextAssembler.FocusContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAiMeter;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort.BillingContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;

class EntityExtractionServiceTest {

    private static final String VALID_LITERAL =
            """
            {"subject":"水温","subjectType":"METRIC","subjectDesc":"","predicate":"等于","object":"20°C","objectKind":"LITERAL","objectType":"","objectDesc":"","evidenceQuote":"水温等于20°C","startOffset":0,"endOffset":8,"confidence":0.9}
            """
                    .strip();

    private final EntityExtractionService service =
            new EntityExtractionService(
                    mock(DynamicChatClientFactory.class),
                    mock(KnowledgeAiMeter.class),
                    mock(IncrementalEntityResolver.class),
                    mock(TrustedKnowledgeStore.class));

    @Test
    @DisplayName("Given evidence offset 超出焦点块 When 校验证据 Then 拒绝持久化事实")
    void should_reject_when_evidence_offset_is_outside_focus() {
        var triple = triple("PostgreSQL", 0, 20);

        assertThatThrownBy(() -> service.validateFocusEvidence("PostgreSQL 是真理源", triple))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("区间越界");
    }

    @Test
    @DisplayName("Given evidence 文本与焦点区间不一致 When 校验证据 Then 拒绝持久化事实")
    void should_reject_when_evidence_does_not_match_focus_exactly() {
        var triple = triple("Neo4j", 0, 6);

        assertThatThrownBy(() -> service.validateFocusEvidence("PostgreSQL 是真理源", triple))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("逐字来自焦点块");
    }

    @Test
    @DisplayName("Given 抽取结果客体为字面量 When 持久化事实 Then 只解析主体并写 LITERAL 事实")
    void should_persist_literal_without_resolving_object_entity() {
        var fixture = fixture("[" + VALID_LITERAL + "]");
        var subjectId = UUID.randomUUID();
        when(fixture.resolver().resolve(any(), any())).thenReturn(subjectId);

        var count = fixture.extract();

        assertThat(count).isEqualTo(1);
        verify(fixture.resolver(), times(1)).resolve(any(), any());
        verify(fixture.store())
                .persistLiteralFact(
                        fixture.run(),
                        subjectId,
                        "等于",
                        "20°C",
                        0.9,
                        fixture.chunkId(),
                        "水温等于20°C",
                        0,
                        8,
                        List.of(),
                        new TrustedKnowledgeStore.FactAssertion(null, null, Map.of()));
    }

    @Test
    @DisplayName("Given 响应包含未知字段 When 解析 Then 整个抽取单元失败")
    void should_reject_unknown_field() {
        var response = "[" + VALID_LITERAL.replace("}", ",\"extra\":true}") + "]";

        assertInvalidResponse(response, "未知=[extra]");
    }

    @Test
    @DisplayName("Given 响应缺少必填字段 When 解析 Then 整个抽取单元失败")
    void should_reject_missing_field() {
        var response = "[" + VALID_LITERAL.replace(",\"confidence\":0.9", "") + "]";

        assertInvalidResponse(response, "缺失=[confidence]");
    }

    @Test
    @DisplayName("Given 响应字段类型错误 When 解析 Then 整个抽取单元失败")
    void should_reject_wrong_field_type() {
        var response = "[" + VALID_LITERAL.replace("\"startOffset\":0", "\"startOffset\":\"0\"") + "]";

        assertInvalidResponse(response, "startOffset 必须是 32 位整数");
    }

    @Test
    @DisplayName("Given objectKind 或实体类型不受支持 When 解析 Then 整个抽取单元失败")
    void should_reject_invalid_object_kind_and_entity_type() {
        assertInvalidResponse(
                "[" + VALID_LITERAL.replace("\"LITERAL\"", "\"OTHER\"") + "]",
                "objectKind");
        var invalidEntityType =
                VALID_LITERAL
                        .replace("\"LITERAL\"", "\"ENTITY\"")
                        .replace("\"objectType\":\"\"", "\"objectType\":\"UNKNOWN\"");
        assertInvalidResponse("[" + invalidEntityType + "]", "objectType");
    }

    @Test
    @DisplayName("Given confidence 非有限或越界 When 校验事实 Then 拒绝持久化")
    void should_reject_invalid_confidence() {
        for (var confidence : List.of(-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY)) {
            var triple = triple("PostgreSQL", 0, 10, confidence);

            assertThatThrownBy(() -> service.validateFocusEvidence("PostgreSQL 是真理源", triple))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("置信度");
        }
        assertInvalidResponse(
                "[" + VALID_LITERAL.replace("\"confidence\":0.9", "\"confidence\":1.1") + "]",
                "置信度");
    }

    @Test
    @DisplayName("Given validAt 不早于 invalidAt When 解析 Then 整个抽取单元失败")
    void should_reject_reversed_valid_interval() {
        var temporal =
                VALID_LITERAL.replace(
                        "\"confidence\":0.9",
                        "\"confidence\":0.9,\"validAt\":\"2026-02-01T00:00:00Z\","
                                + "\"invalidAt\":\"2026-01-01T00:00:00Z\"");

        assertInvalidResponse("[" + temporal + "]", "validAt 必须早于 invalidAt");
    }

    @Test
    @DisplayName("Given attributes 含嵌套对象 When 解析 Then 全量校验失败且不持久化")
    void should_reject_nested_attribute_object() {
        var attributed =
                VALID_LITERAL.replace(
                        "\"confidence\":0.9",
                        "\"confidence\":0.9,\"attributes\":{\"region\":{\"name\":\"华东\"}}");

        assertInvalidResponse("[" + attributed + "]", "不支持嵌套对象");
    }

    @Test
    @DisplayName("Given attributes 伪装 validAt When 解析 Then 拒绝保留系统字段")
    void should_reject_reserved_temporal_attribute_key() {
        var attributed =
                VALID_LITERAL.replace(
                        "\"confidence\":0.9",
                        "\"confidence\":0.9,\"attributes\":{\"validAt\":\"2026-01-01\"}");

        assertInvalidResponse("[" + attributed + "]", "包含保留键: validAt");
    }

    @Test
    @DisplayName("Given 合法可选时态和标量属性 When 持久化 Then 原样写入事实断言")
    void should_persist_optional_temporal_assertion() {
        var response =
                VALID_LITERAL.replace(
                        "\"confidence\":0.9",
                        "\"confidence\":0.9,\"validAt\":\"2026-01-01T00:00:00Z\","
                                + "\"invalidAt\":null,\"attributes\":{\"region\":\"华东\"}");
        var fixture = fixture("[" + response + "]");
        var subjectId = UUID.randomUUID();
        when(fixture.resolver().resolve(any(), any())).thenReturn(subjectId);

        fixture.extract();

        verify(fixture.store())
                .persistLiteralFact(
                        fixture.run(),
                        subjectId,
                        "等于",
                        "20°C",
                        0.9,
                        fixture.chunkId(),
                        "水温等于20°C",
                        0,
                        8,
                        List.of(),
                        new TrustedKnowledgeStore.FactAssertion(
                                Instant.parse("2026-01-01T00:00:00Z"),
                                null,
                                Map.of("region", "华东")));
    }

    @Test
    @DisplayName("Given 单块响应超过事实数量上限 When 解析 Then 在任何持久化前失败")
    void should_reject_more_than_maximum_facts() {
        var response =
                IntStream.rangeClosed(0, EntityExtractionPrompt.MAX_FACTS_PER_CHUNK)
                        .mapToObj(ignored -> VALID_LITERAL)
                        .collect(Collectors.joining(",", "[", "]"));

        assertInvalidResponse(response, "最多抽取");
    }

    @Test
    @DisplayName("Given 数组后一项无效 When 解析 Then 前一项也不得持久化")
    void should_validate_all_items_before_any_persistence() {
        var invalid = VALID_LITERAL.replace("\"endOffset\":8", "\"endOffset\":7");
        var fixture = fixture("[" + VALID_LITERAL + "," + invalid + "]");

        assertThatThrownBy(fixture::extract)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("逐字来自焦点块");
        verifyNoInteractions(fixture.resolver(), fixture.store());
    }

    @Test
    @DisplayName("Given Markdown 围栏包裹 JSON When 解析 Then 不做修复并直接失败")
    void should_not_strip_markdown_fences() {
        assertInvalidResponse("```json\n[" + VALID_LITERAL + "]\n```", "有效 JSON");
    }

    private void assertInvalidResponse(String response, String message) {
        var fixture = fixture(response);

        assertThatThrownBy(fixture::extract)
                .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class)
                .hasMessageContaining(message);
        verifyNoInteractions(fixture.resolver(), fixture.store());
    }

    private Fixture fixture(String response) {
        var meter = mock(KnowledgeAiMeter.class);
        var resolver = mock(IncrementalEntityResolver.class);
        var store = mock(TrustedKnowledgeStore.class);
        var localService =
                new EntityExtractionService(
                        mock(DynamicChatClientFactory.class), meter, resolver, store);
        var runId = UUID.randomUUID();
        var run =
                new RunContext(
                        runId,
                        1,
                        10L,
                        20L,
                        new BillingContext("tenant", runId, 30L),
                        null,
                        1,
                        "PROCESSING",
                        "extraction prompt",
                        "prompt-digest",
                        EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION,
                        "extraction-model",
                        "resolution prompt",
                        "resolution-prompt-digest",
                        EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION,
                        "resolution-model");
        var chunkId = UUID.randomUUID();
        when(meter.invokeText(any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(response);
        return new Fixture(localService, meter, resolver, store, run, chunkId);
    }

    private ExtractedTriple triple(String evidence, int start, int end) {
        return triple(evidence, start, end, 0.95);
    }

    private ExtractedTriple triple(String evidence, int start, int end, double confidence) {
        return new ExtractedTriple(
                "PostgreSQL",
                "SYSTEM",
                "",
                "IS_TRUTH_SOURCE",
                "Knowledge",
                "ENTITY",
                "CONCEPT",
                "",
                evidence,
                start,
                end,
                confidence,
                null,
                null,
                Map.of());
    }

    private record Fixture(
            EntityExtractionService service,
            KnowledgeAiMeter meter,
            IncrementalEntityResolver resolver,
            TrustedKnowledgeStore store,
            RunContext run,
            UUID chunkId) {

        private int extract() {
            return service.extractAndPersist(
                    run, new FocusContext(chunkId, "水温等于20°C", List.of(), List.of()), () -> {});
        }
    }
}
