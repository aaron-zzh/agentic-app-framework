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
        var fixture = fixture(List.of(literal()));
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
    @DisplayName("Given objectKind 不受支持或 ENTITY 类型为空 When 校验 Then 整个抽取单元失败")
    void should_reject_invalid_object_kind_and_entity_type() {
        assertInvalidTriples(
                List.of(
                        new ExtractedTriple(
                                "水温",
                                ExtractedEntityType.METRIC,
                                "",
                                "等于",
                                "20°C",
                                "OTHER",
                                "",
                                "",
                                "水温等于20°C",
                                0,
                                8,
                                0.9,
                                null,
                                null,
                                Map.of())),
                "objectKind");
        assertInvalidTriples(
                List.of(
                        new ExtractedTriple(
                                "PostgreSQL",
                                ExtractedEntityType.SYSTEM,
                                "",
                                "IS_TRUTH_SOURCE",
                                "Knowledge",
                                "ENTITY",
                                " ",
                                "",
                                "PostgreSQL",
                                0,
                                10,
                                0.9,
                                null,
                                null,
                                Map.of())),
                "objectType");
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
        assertInvalidTriples(
                List.of(
                        new ExtractedTriple(
                                "水温",
                                ExtractedEntityType.METRIC,
                                "",
                                "等于",
                                "20°C",
                                "LITERAL",
                                "",
                                "",
                                "水温等于20°C",
                                0,
                                8,
                                1.1,
                                null,
                                null,
                                Map.of())),
                "置信度");
    }

    @Test
    @DisplayName("Given validAt 不早于 invalidAt When 校验 Then 整个抽取单元失败")
    void should_reject_reversed_valid_interval() {
        assertInvalidTriples(
                List.of(
                        new ExtractedTriple(
                                "水温",
                                ExtractedEntityType.METRIC,
                                "",
                                "等于",
                                "20°C",
                                "LITERAL",
                                "",
                                "",
                                "水温等于20°C",
                                0,
                                8,
                                0.9,
                                Instant.parse("2026-02-01T00:00:00Z"),
                                Instant.parse("2026-01-01T00:00:00Z"),
                                Map.of())),
                "validAt 必须早于 invalidAt");
    }

    @Test
    @DisplayName("Given attributes 含嵌套对象 When 校验 Then 全量校验失败且不持久化")
    void should_reject_nested_attribute_object() {
        assertInvalidTriples(
                List.of(
                        new ExtractedTriple(
                                "水温",
                                ExtractedEntityType.METRIC,
                                "",
                                "等于",
                                "20°C",
                                "LITERAL",
                                "",
                                "",
                                "水温等于20°C",
                                0,
                                8,
                                0.9,
                                null,
                                null,
                                Map.of("region", Map.of("name", "华东")))),
                "不支持嵌套对象");
    }

    @Test
    @DisplayName("Given attributes 伪装 validAt When 校验 Then 拒绝保留系统字段")
    void should_reject_reserved_temporal_attribute_key() {
        assertInvalidTriples(
                List.of(
                        new ExtractedTriple(
                                "水温",
                                ExtractedEntityType.METRIC,
                                "",
                                "等于",
                                "20°C",
                                "LITERAL",
                                "",
                                "",
                                "水温等于20°C",
                                0,
                                8,
                                0.9,
                                null,
                                null,
                                Map.of("validAt", "2026-01-01"))),
                "包含保留键: validAt");
    }

    @Test
    @DisplayName("Given 合法可选时态和标量属性 When 持久化 Then 原样写入事实断言")
    void should_persist_optional_temporal_assertion() {
        var fixture =
                fixture(
                        List.of(
                                new ExtractedTriple(
                                        "水温",
                                        ExtractedEntityType.METRIC,
                                        "",
                                        "等于",
                                        "20°C",
                                        "LITERAL",
                                        "",
                                        "",
                                        "水温等于20°C",
                                        0,
                                        8,
                                        0.9,
                                        Instant.parse("2026-01-01T00:00:00Z"),
                                        null,
                                        Map.of("region", "华东"))));
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
    @DisplayName("Given 单块响应超过事实数量上限 When 校验 Then 在任何持久化前失败")
    void should_reject_more_than_maximum_facts() {
        var triples =
                IntStream.rangeClosed(0, EntityExtractionPrompt.MAX_FACTS_PER_CHUNK)
                        .mapToObj(ignored -> literal())
                        .toList();

        assertInvalidTriples(triples, "最多抽取");
    }

    @Test
    @DisplayName("Given 结构化结果后一项无效 When 校验 Then 前一项也不得持久化")
    void should_validate_all_items_before_any_persistence() {
        var invalid =
                new ExtractedTriple(
                        "水温",
                        ExtractedEntityType.METRIC,
                        "",
                        "等于",
                        "20°C",
                        "LITERAL",
                        "",
                        "",
                        "水温约等于20°C",
                        0,
                        9,
                        0.9,
                        null,
                        null,
                        Map.of());
        var fixture = fixture(List.of(literal(), invalid));

        assertThatThrownBy(fixture::extract)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("逐字来自焦点块");
        verifyNoInteractions(fixture.resolver(), fixture.store());
    }

    private void assertInvalidTriples(List<ExtractedTriple> triples, String message) {
        var fixture = fixture(triples);

        assertThatThrownBy(fixture::extract)
                .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class)
                .hasMessageContaining(message);
        verifyNoInteractions(fixture.resolver(), fixture.store());
    }

    private Fixture fixture(List<ExtractedTriple> triples) {
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
                        1,
                        "extraction user prompt",
                        "user-prompt-digest",
                        1,
                        EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION,
                        "extraction-model",
                        "resolution prompt",
                        "resolution-prompt-digest",
                        1,
                        "resolution user prompt",
                        "resolution-user-prompt-digest",
                        1,
                        EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION,
                        "resolution-model");
        var chunkId = UUID.randomUUID();
        when(meter.invokeEntityExtraction(
                        any(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(triples);
        return new Fixture(localService, resolver, store, run, chunkId);
    }

    private ExtractedTriple literal() {
        return new ExtractedTriple(
                "水温",
                ExtractedEntityType.METRIC,
                "",
                "等于",
                "20°C",
                "LITERAL",
                "",
                "",
                "水温等于20°C",
                0,
                8,
                0.9,
                null,
                null,
                Map.of());
    }

    private ExtractedTriple triple(String evidence, int start, int end) {
        return triple(evidence, start, end, 0.95);
    }

    private ExtractedTriple triple(String evidence, int start, int end, double confidence) {
        return new ExtractedTriple(
                "PostgreSQL",
                ExtractedEntityType.SYSTEM,
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
