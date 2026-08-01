package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * 实体消歧服务：异步批量识别并合并同知识库内指向同一真实世界对象的重复实体。
 *
 * <p>解决 {@link EntityExtractionService} 按 chunk 独立抽取导致的碎片化问题——不同 chunk 抽出的
 * "张三"/"张经理"等表述会先被当成不同实体落库（见 {@code EntityExtractionService#findOrCreateEntity}
 * 的精确名称匹配），本服务在文档处理完成后做一次批量扫描，把它们合并成一个节点。
 *
 * <p>流程分三步，对应 {@code nexus-knowledge.md} ECL 管道 Load 阶段的"冲突检测与合并（同义实体）"：
 *
 * <ol>
 *   <li>粗筛：对每个实体的 name+description 计算 embedding，按余弦相似度用并查集分组，圈出候选组
 *   <li>终审：候选组交给 LLM 判断哪些确实是同一对象（相似度高不代表语义相同，如"苹果公司"/"梨公司"）
 *   <li>合并：对 LLM 认可的组调用 {@link GraphService#mergeEntities}，关系转移到保留节点，删除重复节点
 * </ol>
 *
 * <p>只做粗筛+终审两层过滤是成本和精度的权衡：粗筛用 embedding 便宜但会有假阳性（词形相似但语义不同）， 终审用 LLM
 * 贵但准确，只对粗筛圈出的候选组调用，不需要对全量实体两两比较。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityResolutionService {

    /** 余弦相似度阈值：超过此值才进入候选组，避免把词形相似但语义不同的实体也圈进来 */
    private static final double SIMILARITY_THRESHOLD = 0.92;

    private final KnowledgeEntityRepository entityRepository;
    private final GraphService graphService;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseOwnerPort ownerPort;
    private final ChatClient.Builder chatClientBuilder;

    /**
     * 对指定知识库做一次实体消歧扫描。
     *
     * <p>知识库不存在或已删除、或知识库内实体数不足 2 个时直接跳过，不产生任何调用。
     */
    public void resolve(Long knowledgeBaseId) {
        var entities = entityRepository.findByKnowledgeBaseId(knowledgeBaseId);
        if (entities.size() < 2) {
            return;
        }
        var ownerId = ownerPort.findOwnerId(knowledgeBaseId);
        if (ownerId == null) {
            log.warn("知识库不存在或已删除，跳过实体消歧: knowledgeBaseId={}", knowledgeBaseId);
            return;
        }

        var groups = groupBySimilarity(entities, ownerId);
        if (groups.isEmpty()) {
            return;
        }

        var decisions = reviewGroups(groups);
        var entityById = new HashMap<String, KnowledgeEntity>();
        entities.forEach(entity -> entityById.put(entity.getId(), entity));

        var mergedCount = 0;
        for (var decision : decisions) {
            if (decision.mergeIds() == null || decision.mergeIds().size() < 2) {
                continue;
            }
            var keepId = decision.mergeIds().get(0);
            var duplicateIds = decision.mergeIds().subList(1, decision.mergeIds().size());
            if (!entityById.containsKey(keepId)
                    || !duplicateIds.stream().allMatch(entityById::containsKey)) {
                log.warn("实体消歧终审返回了不存在的实体 id，跳过该组: {}", decision);
                continue;
            }
            graphService.mergeEntities(keepId, duplicateIds);
            mergedCount += duplicateIds.size();
        }
        log.info("知识库 {} 实体消歧完成：候选组 {} 个，合并 {} 个重复实体", knowledgeBaseId, groups.size(), mergedCount);
    }

    /** 用余弦相似度 + 并查集给实体分组，只保留成员数 ≥ 2 的组作为候选。 */
    private List<List<KnowledgeEntity>> groupBySimilarity(
            List<KnowledgeEntity> entities, Long ownerId) {
        var texts = entities.stream().map(this::embeddingText).toList();
        var vectors = embeddingService.embedBatch(texts, 20, ownerId);

        var parent = new int[entities.size()];
        for (var i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (var i = 0; i < entities.size(); i++) {
            for (var j = i + 1; j < entities.size(); j++) {
                if (cosineSimilarity(vectors.get(i), vectors.get(j)) >= SIMILARITY_THRESHOLD) {
                    union(parent, i, j);
                }
            }
        }

        var groupsByRoot = new HashMap<Integer, List<KnowledgeEntity>>();
        for (var i = 0; i < entities.size(); i++) {
            groupsByRoot
                    .computeIfAbsent(find(parent, i), ignored -> new ArrayList<>())
                    .add(entities.get(i));
        }
        return groupsByRoot.values().stream().filter(group -> group.size() >= 2).toList();
    }

    private String embeddingText(KnowledgeEntity entity) {
        var description = entity.getDescription();
        return description == null || description.isBlank()
                ? entity.getName()
                : entity.getName() + "：" + description;
    }

    private int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private void union(int[] parent, int a, int b) {
        var rootA = find(parent, a);
        var rootB = find(parent, b);
        if (rootA != rootB) {
            parent[rootA] = rootB;
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (var i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** 把候选组交给 LLM 终审，决定每组内哪些实体确实该合并。 */
    private List<EntityMergeDecision> reviewGroups(List<List<KnowledgeEntity>> groups) {
        var groupsPayload = new ArrayList<Map<String, Object>>();
        for (var index = 0; index < groups.size(); index++) {
            var members =
                    groups.get(index).stream()
                            .map(
                                    entity ->
                                            Map.of(
                                                    "id",
                                                    entity.getId(),
                                                    "name",
                                                    entity.getName(),
                                                    "description",
                                                    entity.getDescription() == null
                                                            ? ""
                                                            : entity.getDescription()))
                            .toList();
            groupsPayload.add(Map.of("groupIndex", index, "entities", members));
        }

        var userPrompt =
                EntityResolutionPrompt.USER_PROMPT_TEMPLATE.replace(
                        "{groups}", JsonUtils.toJsonString(groupsPayload));
        var content =
                chatClientBuilder
                        .build()
                        .prompt()
                        .system(EntityResolutionPrompt.SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .content();
        try {
            return JsonUtils.parseObject(content, new TypeReference<>() {});
        } catch (Exception failure) {
            throw new IllegalStateException("实体消歧终审结果不是有效 JSON", failure);
        }
    }
}
