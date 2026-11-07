package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;
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
import com.xuejiai.aaf.framework.engine.knowledge.trusted.IncrementalEntityResolver.MentionKey;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAiMeter.EntityResolutionBatchDecision;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAiMeter.EntityResolutionDecision;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort.BillingContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.EntityCandidate;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.ScoredEntityCandidate;
import com.xuejiai.aaf.framework.intelligent.ai.chat.StructuredToolCallClient;

@ExtendWith(MockitoExtension.class)
class IncrementalEntityResolverTest {

    private static final Runnable NO_GUARD = () -> {};

    @Mock private TrustedKnowledgeStore store;
    @Mock private StructuredToolCallClient structuredToolCallClient;
    @Mock private KnowledgeAiMeter meter;
    @Mock private EmbeddingService embeddingService;

    private IncrementalEntityResolver resolver;
    private RunContext run;

    @BeforeEach
    void setUp() {
        resolver =
                new IncrementalEntityResolver(
                        store,
                        structuredToolCallClient,
                        meter,
                        embeddingService,
                        new EmbeddingProperties("embed-model", 1536, 100, 3),
                        new EntityResolutionProperties(0.2, 0.05));
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
    @DisplayName("Given 精确 alias 已存在 When 批量消歧 Then 直接复用且不查询候选")
    void should_reuse_exact_alias_without_candidate_search() {
        var entityId = UUID.randomUUID();
        when(store.findExactEntities(10L, "nexuskb", "system"))
                .thenReturn(List.of(new EntityCandidate(entityId, "NexusKB", "SYSTEM", null)));
        var mention = new EntityMention("NexusKB", "SYSTEM", null);

        var resolved = resolver.resolveAll(run, List.of(mention), NO_GUARD);

        assertThat(resolved).containsEntry(MentionKey.of(mention), entityId);
        verify(store, never())
                .findEntityCandidates(
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(float[].class),
                        anyDouble(),
                        anyInt());
        verify(embeddingService, never()).embedKnowledgeBatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("Given 文档内同名同类型 mention 重复 When 批量消歧 Then 只处理一次")
    void should_deduplicate_mentions_within_document() {
        var entityId = UUID.randomUUID();
        when(store.findExactEntities(10L, "nexuskb", "system"))
                .thenReturn(List.of(new EntityCandidate(entityId, "NexusKB", "SYSTEM", null)));
        var first = new EntityMention("NexusKB", "SYSTEM", "描述一");
        var second = new EntityMention("NexusKB", "SYSTEM", "描述二");

        var resolved = resolver.resolveAll(run, List.of(first, second), NO_GUARD);

        assertThat(resolved).hasSize(1).containsEntry(MentionKey.of(first), entityId);
        verify(store).findExactEntities(10L, "nexuskb", "system");
    }

    @Test
    @DisplayName("Given 无精确 alias 且门槛内无候选 When 批量消歧 Then 一次向量化并直接创建实体")
    void should_create_without_model_when_no_candidate_within_threshold() {
        var createdId = UUID.randomUUID();
        var vector = new float[] {0.1f, 0.2f};
        when(store.findExactEntities(10L, "postgresql", "database")).thenReturn(List.of());
        when(embeddingService.embedKnowledgeBatch(any(), eq(run.billing()), anyString()))
                .thenReturn(List.of(vector));
        when(store.findEntityCandidates(10L, "postgresql", "database", vector, 0.2, 5))
                .thenReturn(List.of());
        when(store.createOrAliasEntity(
                        10L, run.runId(), "PostgreSQL", "DATABASE", null, "postgresql", false))
                .thenReturn(createdId);
        var mention = new EntityMention("PostgreSQL", "DATABASE", null);

        var resolved = resolver.resolveAll(run, List.of(mention), NO_GUARD);

        assertThat(resolved).containsEntry(MentionKey.of(mention), createdId);
        verify(store).upsertEntityEmbedding(createdId, 10L, run.runId(), "embed-model", vector);
        verify(meter, never())
                .invokeEntityResolution(
                        any(), anyString(), anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("Given 首候选距离低于自动关联门槛且类型一致 When 批量消歧 Then 不调用模型直接关联")
    void should_auto_link_high_similarity_candidate() {
        var candidateId = UUID.randomUUID();
        var vector = new float[] {0.3f};
        when(store.findExactEntities(10L, "pg", "system")).thenReturn(List.of());
        when(embeddingService.embedKnowledgeBatch(any(), eq(run.billing()), anyString()))
                .thenReturn(List.of(vector));
        when(store.findEntityCandidates(10L, "pg", "system", vector, 0.2, 5))
                .thenReturn(
                        List.of(
                                new ScoredEntityCandidate(
                                        new EntityCandidate(
                                                candidateId, "PostgreSQL", "SYSTEM", null),
                                        0.03)));
        var mention = new EntityMention("PG", "SYSTEM", null);

        var resolved = resolver.resolveAll(run, List.of(mention), NO_GUARD);

        assertThat(resolved).containsEntry(MentionKey.of(mention), candidateId);
        verify(store).addAlias(10L, candidateId, run.runId(), "pg", 0.9);
        verify(meter, never())
                .invokeEntityResolution(
                        any(), anyString(), anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("Given 多个候选且模型返回有效 LINK When 批量消歧 Then 使用冻结配置链接候选")
    void should_link_candidate_with_frozen_configuration() {
        var candidates = stubAmbiguous();
        stubDecisions(new EntityResolutionDecision("m0", "LINK", candidates.selected().toString()));

        var resolved = resolver.resolveAll(run, List.of(mention()), NO_GUARD);

        assertThat(resolved).containsEntry(MentionKey.of(mention()), candidates.selected());
        verify(store).addAlias(10L, candidates.selected(), run.runId(), "aaf", 0.9);
        verify(meter)
                .invokeEntityResolution(
                        eq(run.billing()),
                        eq("knowledge-resolution"),
                        argThat(
                                unitKey ->
                                        unitKey.startsWith("mentions:")
                                                && unitKey.endsWith(
                                                        EntityResolutionPrompt
                                                                .STRUCTURED_OUTPUT_FORMAT_VERSION)),
                        eq("resolution-model"),
                        argThat(
                                input ->
                                        input.startsWith(
                                                "resolution-prompt-digest|"
                                                        + EntityResolutionPrompt
                                                                .OUTPUT_CONTRACT_VERSION
                                                        + "|")),
                        eq(EntityResolutionPrompt.MAX_RESPONSE_LENGTH),
                        any());
    }

    @Test
    @DisplayName("Given 模型返回 CREATE When 批量消歧 Then 创建普通实体")
    void should_create_entity_when_decision_is_create() {
        stubAmbiguous();
        stubDecisions(new EntityResolutionDecision("m0", "CREATE", null));
        var createdId = UUID.randomUUID();
        when(store.createOrAliasEntity(10L, run.runId(), "AAF", "CONCEPT", null, "aaf", false))
                .thenReturn(createdId);

        assertThat(resolver.resolveAll(run, List.of(mention()), NO_GUARD))
                .containsEntry(MentionKey.of(mention()), createdId);
    }

    @Test
    @DisplayName("Given 模型返回 REVIEW When 批量消歧 Then 创建待审实体")
    void should_create_review_entity_when_decision_is_review() {
        stubAmbiguous();
        stubDecisions(new EntityResolutionDecision("m0", "REVIEW", null));
        var createdId = UUID.randomUUID();
        when(store.createOrAliasEntity(10L, run.runId(), "AAF", "CONCEPT", null, "aaf", true))
                .thenReturn(createdId);

        assertThat(resolver.resolveAll(run, List.of(mention()), NO_GUARD))
                .containsEntry(MentionKey.of(mention()), createdId);
    }

    @Test
    @DisplayName("Given 消歧 action 未知 When 校验结构化结果 Then 丢弃该 mention 且不静默 CREATE")
    void should_reject_unknown_action() {
        stubAmbiguous();
        stubDecisions(new EntityResolutionDecision("m0", "MERGE", null));

        assertUnresolved();
    }

    @Test
    @DisplayName("Given LINK 指向候选外 UUID When 校验结构化结果 Then 丢弃该 mention 且不静默 CREATE")
    void should_reject_link_outside_candidates() {
        stubAmbiguous();
        stubDecisions(new EntityResolutionDecision("m0", "LINK", UUID.randomUUID().toString()));

        assertUnresolved();
    }

    @Test
    @DisplayName("Given CREATE 携带非空 entityId When 校验结构化结果 Then 丢弃该 mention 且不写实体")
    void should_reject_entity_id_for_create() {
        stubAmbiguous();
        stubDecisions(new EntityResolutionDecision("m0", "CREATE", UUID.randomUUID().toString()));

        assertUnresolved();
    }

    @Test
    @DisplayName("Given 模型漏返回某个 key When 校验结构化结果 Then 丢弃该 mention")
    void should_reject_missing_decision_key() {
        stubAmbiguous();
        stubDecisions(new EntityResolutionDecision("m9", "CREATE", null));

        assertUnresolved();
    }

    @Test
    @DisplayName("Given 知识库已有 ORGANIZATION 苹果 When 新文档抽取 PRODUCT 苹果 Then 不复用并新建 PRODUCT 实体")
    void should_not_reuse_entity_when_same_name_but_different_type() {
        var createdId = UUID.randomUUID();
        var vector = new float[] {0.4f};
        when(store.findExactEntities(10L, "苹果", "product")).thenReturn(List.of());
        when(embeddingService.embedKnowledgeBatch(any(), eq(run.billing()), anyString()))
                .thenReturn(List.of(vector));
        when(store.findEntityCandidates(10L, "苹果", "product", vector, 0.2, 5))
                .thenReturn(List.of());
        when(store.createOrAliasEntity(10L, run.runId(), "苹果", "PRODUCT", null, "苹果", false))
                .thenReturn(createdId);
        var mention = new EntityMention("苹果", "PRODUCT", null);

        var resolved = resolver.resolveAll(run, List.of(mention), NO_GUARD);

        assertThat(resolved).containsExactly(Map.entry(MentionKey.of(mention), createdId));
        verify(store, never()).findExactEntities(10L, "苹果", "organization");
        verify(store, never()).addAlias(anyLong(), any(), any(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("Given 同一文档同时出现 ORGANIZATION 与 PRODUCT 苹果 When 批量消歧 Then 解析为两个实体")
    void should_resolve_two_entities_when_same_document_has_two_types() {
        var organizationId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var vector = new float[] {0.5f};
        when(store.findExactEntities(10L, "苹果", "organization"))
                .thenReturn(
                        List.of(new EntityCandidate(organizationId, "苹果", "ORGANIZATION", null)));
        when(store.findExactEntities(10L, "苹果", "product")).thenReturn(List.of());
        when(embeddingService.embedKnowledgeBatch(any(), eq(run.billing()), anyString()))
                .thenReturn(List.of(vector));
        when(store.findEntityCandidates(10L, "苹果", "product", vector, 0.2, 5))
                .thenReturn(List.of());
        when(store.createOrAliasEntity(10L, run.runId(), "苹果", "PRODUCT", null, "苹果", false))
                .thenReturn(productId);
        var organization = new EntityMention("苹果", "ORGANIZATION", null);
        var product = new EntityMention("苹果", "PRODUCT", null);

        var resolved = resolver.resolveAll(run, List.of(organization, product), NO_GUARD);

        assertThat(resolved)
                .hasSize(2)
                .containsEntry(MentionKey.of(organization), organizationId)
                .containsEntry(MentionKey.of(product), productId);
    }

    @Test
    @DisplayName("Given 知识库已有 ORGANIZATION 苹果 When 新文档抽取 ORGANIZATION 苹果 Then 复用已有实体")
    void should_reuse_entity_when_same_name_and_same_type() {
        var organizationId = UUID.randomUUID();
        when(store.findExactEntities(10L, "苹果", "organization"))
                .thenReturn(
                        List.of(new EntityCandidate(organizationId, "苹果", "ORGANIZATION", null)));
        var mention = new EntityMention("苹果", " Organization ", null);

        var resolved = resolver.resolveAll(run, List.of(mention), NO_GUARD);

        assertThat(resolved).containsExactly(Map.entry(MentionKey.of(mention), organizationId));
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

    @Test
    @DisplayName("Given mention 类型为空 When 批量消歧 Then 以写入类型 Concept 匹配并创建")
    void should_match_with_default_type_when_mention_type_is_blank() {
        var createdId = UUID.randomUUID();
        var vector = new float[] {0.6f};
        when(store.findExactEntities(10L, "知识", "concept")).thenReturn(List.of());
        when(embeddingService.embedKnowledgeBatch(any(), eq(run.billing()), anyString()))
                .thenReturn(List.of(vector));
        when(store.findEntityCandidates(10L, "知识", "concept", vector, 0.2, 5))
                .thenReturn(List.of());
        when(store.createOrAliasEntity(10L, run.runId(), "知识", "Concept", null, "知识", false))
                .thenReturn(createdId);
        var mention = new EntityMention("知识", " ", null);

        var resolved = resolver.resolveAll(run, List.of(mention), NO_GUARD);

        assertThat(MentionKey.of(mention).normalizedType()).isEqualTo("concept");
        assertThat(resolved).containsEntry(MentionKey.of(mention), createdId);
    }

    private CandidateIds stubAmbiguous() {
        var first = UUID.randomUUID();
        var selected = UUID.randomUUID();
        when(store.findExactEntities(10L, "aaf", "concept"))
                .thenReturn(
                        List.of(
                                new EntityCandidate(first, "AAF", "CONCEPT", "描述一"),
                                new EntityCandidate(selected, "AAF", "CONCEPT", "描述二")));
        return new CandidateIds(first, selected);
    }

    private void stubDecisions(EntityResolutionDecision... decisions) {
        when(meter.invokeEntityResolution(
                        eq(run.billing()),
                        eq("knowledge-resolution"),
                        anyString(),
                        eq("resolution-model"),
                        anyString(),
                        eq(EntityResolutionPrompt.MAX_RESPONSE_LENGTH),
                        any()))
                .thenReturn(new EntityResolutionBatchDecision(List.of(decisions)));
    }

    private void assertUnresolved() {
        Map<MentionKey, UUID> resolved = resolver.resolveAll(run, List.of(mention()), NO_GUARD);

        assertThat(resolved).isEmpty();
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
