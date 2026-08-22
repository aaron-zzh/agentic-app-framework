package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver.EntityMention;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort.BillingContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DynamicChatClientFactory;

@ExtendWith(MockitoExtension.class)
class IncrementalEntityResolverTest {

    @Mock private TrustedKnowledgeStore store;
    @Mock private DynamicChatClientFactory chatClientFactory;
    @Mock private KnowledgeAiMeter meter;
    @Mock private EmbeddingService embeddingService;

    private IncrementalEntityResolver resolver;
    private RunContext run;

    @BeforeEach
    void setUp() {
        resolver =
                new IncrementalEntityResolver(
                        store,
                        chatClientFactory,
                        meter,
                        embeddingService,
                        new EmbeddingProperties("embed-model", 1536, 100, 3, 1));
        var runId = UUID.randomUUID();
        run =
                new RunContext(
                        runId,
                        1,
                        10L,
                        20L,
                        new BillingContext("tenant-1", runId, 30L),
                        null,
                        1,
                        "PROCESSING",
                        "extraction prompt",
                        "extraction-prompt-digest",
                        1,
                        "extraction user prompt",
                        "extraction-user-prompt-digest",
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
    }

    @Test
    @DisplayName("Given 精确 alias 已存在 When 消歧新增 mention Then 直接复用且不查询候选")
    void should_reuse_exact_alias_without_candidate_search() {
        var entityId = UUID.randomUUID();
        when(store.findExactEntities(10L, "nexuskb"))
                .thenReturn(
                        List.of(
                                new TrustedKnowledgeStore.EntityCandidate(
                                        entityId, "NexusKB", "SYSTEM", null)));

        var resolved = resolver.resolve(run, new EntityMention("NexusKB", "SYSTEM", null));

        assertThat(resolved).isEqualTo(entityId);
        verify(store, never())
                .findEntityCandidates(anyLong(), anyString(), any(float[].class), anyInt());
        verify(embeddingService, never()).embedKnowledgeBatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("Given 无精确 alias 且无候选 When 消歧新增 mention Then 最多查询五个候选并创建实体")
    void should_limit_candidates_and_create_when_no_candidate_exists() {
        var createdId = UUID.randomUUID();
        var vector = new float[] {0.1f, 0.2f};
        when(store.findExactEntities(10L, "postgresql")).thenReturn(List.of());
        when(embeddingService.embedKnowledgeBatch(any(), eq(run.billing()), anyString()))
                .thenReturn(List.of(vector));
        when(store.findEntityCandidates(10L, "postgresql", vector, 5)).thenReturn(List.of());
        when(store.createOrAliasEntity(
                        10L, run.runId(), "PostgreSQL", "DATABASE", null, "postgresql", false))
                .thenReturn(createdId);

        var resolved = resolver.resolve(run, new EntityMention("PostgreSQL", "DATABASE", null));

        assertThat(resolved).isEqualTo(createdId);
        verify(store).findEntityCandidates(10L, "postgresql", vector, 5);
        verify(store).upsertEntityEmbedding(createdId, 10L, run.runId(), "embed-model", vector);
    }

    @Test
    @DisplayName("Given 多个候选且模型返回有效 LINK When 消歧 Then 使用冻结配置链接候选")
    void should_link_candidate_with_frozen_configuration() {
        var candidates = stubAmbiguous("placeholder");
        when(meter.invokeText(
                        eq(run.billing()),
                        eq("knowledge-resolution"),
                        eq("mention:aaf"),
                        eq("resolution-model"),
                        anyString(),
                        any()))
                .thenReturn(
                        "{\"action\":\"LINK\",\"entityId\":\"%s\"}"
                                .formatted(candidates.selected()));

        var resolved = resolver.resolve(run, mention());

        assertThat(resolved).isEqualTo(candidates.selected());
        verify(store).addAlias(10L, candidates.selected(), run.runId(), "aaf", 0.9);
        verify(meter)
                .invokeText(
                        eq(run.billing()),
                        eq("knowledge-resolution"),
                        eq("mention:aaf"),
                        eq("resolution-model"),
                        argThat(
                                input ->
                                        input.startsWith(
                                                "resolution-prompt-digest|"
                                                        + EntityResolutionPrompt
                                                                .OUTPUT_CONTRACT_VERSION
                                                        + "|")),
                        any());
    }

    @Test
    @DisplayName("Given 模型返回 CREATE When 消歧 Then 创建普通实体")
    void should_create_entity_when_decision_is_create() {
        stubAmbiguous("{\"action\":\"CREATE\",\"entityId\":null}");
        var createdId = UUID.randomUUID();
        when(store.createOrAliasEntity(10L, run.runId(), "AAF", "CONCEPT", null, "aaf", false))
                .thenReturn(createdId);

        assertThat(resolver.resolve(run, mention())).isEqualTo(createdId);
    }

    @Test
    @DisplayName("Given 模型返回 REVIEW When 消歧 Then 创建待审实体")
    void should_create_review_entity_when_decision_is_review() {
        stubAmbiguous("{\"action\":\"REVIEW\",\"entityId\":null}");
        var createdId = UUID.randomUUID();
        when(store.createOrAliasEntity(10L, run.runId(), "AAF", "CONCEPT", null, "aaf", true))
                .thenReturn(createdId);

        assertThat(resolver.resolve(run, mention())).isEqualTo(createdId);
    }

    @Test
    @DisplayName("Given 消歧响应包含未知字段 When 解析 Then 失败且不写实体")
    void should_reject_unknown_field() {
        stubAmbiguous("{\"action\":\"CREATE\",\"entityId\":null,\"reason\":\"similar\"}");

        assertInvalidDecision("字段集合不匹配");
    }

    @Test
    @DisplayName("Given 消歧响应缺少 entityId When 解析 Then 失败且不写实体")
    void should_reject_missing_field() {
        stubAmbiguous("{\"action\":\"CREATE\"}");

        assertInvalidDecision("缺失=[entityId]");
    }

    @Test
    @DisplayName("Given 消歧 action 未知 When 解析 Then 失败且不静默 CREATE")
    void should_reject_unknown_action() {
        stubAmbiguous("{\"action\":\"MERGE\",\"entityId\":null}");

        assertInvalidDecision("action 不受支持");
    }

    @Test
    @DisplayName("Given LINK 指向候选外 UUID When 解析 Then 失败且不静默 CREATE")
    void should_reject_link_outside_candidates() {
        stubAmbiguous("{\"action\":\"LINK\",\"entityId\":\"%s\"}".formatted(UUID.randomUUID()));

        assertInvalidDecision("不在当前候选集合");
    }

    @Test
    @DisplayName("Given CREATE 携带非空 entityId When 解析 Then 失败且不写实体")
    void should_reject_entity_id_for_create() {
        stubAmbiguous("{\"action\":\"CREATE\",\"entityId\":\"%s\"}".formatted(UUID.randomUUID()));

        assertInvalidDecision("必须是 null");
    }

    @Test
    @DisplayName("Given Markdown 围栏包裹消歧 JSON When 解析 Then 不做修复并直接失败")
    void should_not_strip_markdown_fences() {
        stubAmbiguous("```json\n{\"action\":\"CREATE\",\"entityId\":null}\n```");

        assertInvalidDecision("有效 JSON");
    }

    private CandidateIds stubAmbiguous(String response) {
        var first = UUID.randomUUID();
        var selected = UUID.randomUUID();
        when(store.findExactEntities(10L, "aaf"))
                .thenReturn(
                        List.of(
                                new TrustedKnowledgeStore.EntityCandidate(
                                        first, "AAF", "PROJECT", null),
                                new TrustedKnowledgeStore.EntityCandidate(
                                        selected, "AAF", "ORGANIZATION", null)));
        if (!"placeholder".equals(response)) {
            when(meter.invokeText(
                            eq(run.billing()),
                            eq("knowledge-resolution"),
                            eq("mention:aaf"),
                            eq("resolution-model"),
                            anyString(),
                            any()))
                    .thenReturn(response);
        }
        return new CandidateIds(first, selected);
    }

    private void assertInvalidDecision(String message) {
        assertThatThrownBy(() -> resolver.resolve(run, mention()))
                .isInstanceOfAny(IllegalArgumentException.class, IllegalStateException.class)
                .hasMessageContaining(message);
        verify(store, never()).addAlias(anyLong(), any(), any(), anyString(), anyDouble());
        verify(store, never())
                .createOrAliasEntity(
                        anyLong(),
                        any(),
                        anyString(),
                        anyString(),
                        nullable(String.class),
                        anyString(),
                        anyBoolean());
    }

    private EntityMention mention() {
        return new EntityMention("AAF", "CONCEPT", null);
    }

    private record CandidateIds(UUID first, UUID selected) {}
}
