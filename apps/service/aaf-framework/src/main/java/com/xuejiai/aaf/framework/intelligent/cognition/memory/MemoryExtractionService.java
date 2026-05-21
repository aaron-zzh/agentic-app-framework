/**
 * LLM 驱动记忆实体/关系抽取（写入时 Agentic）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryRelation;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 从对话文本中抽取结构化记忆原子和关系。 被 Agent 调用时触发，属于认知层 Agentic 能力。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractionService {

    private final ResilientChatService chatService;
    private final EmbeddingService embeddingService;
    private final AtomMemoryEngine atomEngine;
    private final MemoryDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    private static final String EXTRACTION_PROMPT =
            """
        你是记忆抽取专家。从以下对话文本中提取值得长期记忆的事实。

        规则：
        1. 只提取有长期价值的信息（用户偏好、重要事实、关键决策），忽略寒暄和临时信息
        2. 每条记忆应是独立的原子事实，不可再分
        3. 识别记忆间的关系（causal=因果, temporal=时序, associative=关联）

        返回 JSON 格式：
        {
          "atoms": [
            {"content": "记忆内容", "tags": ["标签"], "scope": "long_term"}
          ],
          "relations": [
            {"source_index": 0, "target_index": 1, "type": "associative", "weight": 0.8}
          ]
        }

        如果没有值得记忆的内容，返回 {"atoms": [], "relations": []}
        """;

    /**
     * 从对话中抽取记忆原子（仅抽取，不存储）。供写管道调用。
     *
     * @param userMessage 用户消息
     * @param assistantReply 助手回复
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @return 抽取的原子列表
     */
    public List<MemoryAtom> extract(
            String userMessage, String assistantReply, Long userId, String sessionId) {
        var text = "用户: %s\n助手: %s".formatted(userMessage, assistantReply);
        var extraction = callLlmExtract(text);
        if (extraction == null || extraction.atoms().isEmpty()) {
            return List.of();
        }
        var atoms = new ArrayList<MemoryAtom>();
        for (var raw : extraction.atoms()) {
            var atom = new MemoryAtom();
            atom.setUserId(userId);
            atom.setScope(raw.scope() != null ? raw.scope() : "long_term");
            atom.setContent(raw.content());
            atom.setEmbedding(embeddingService.embed(raw.content()));
            atom.setEventTime(Instant.now());
            atom.setTags(raw.tags());
            atom.setWeight(0.5);
            atoms.add(atom);
        }
        return atoms;
    }

    /**
     * 从对话文本中抽取记忆并存储（完整写入流水线）。
     *
     * <p>流程（对齐认知心理学编码过程）：
     *
     * <ol>
     *   <li>注意筛选：LLM 判断哪些信息值得记忆
     *   <li>结构化：提取实体、关系、标签
     *   <li>编码决策：与已有记忆比对，决定 ADD/UPDATE/DELETE/NONE
     *   <li>持久化：写入 AtomMemoryEngine
     * </ol>
     *
     * @param userId 用户 ID
     * @param conversationText 对话文本
     * @param eventTime 事件发生时间
     * @return 抽取并存储的原子列表
     */
    public List<MemoryAtom> extractAndStore(
            Long userId, String conversationText, Instant eventTime) {
        // 1. LLM 抽取结构化记忆（注意筛选 + 结构化）
        var extraction = callLlmExtract(conversationText);
        if (extraction == null || extraction.atoms().isEmpty()) {
            return List.of();
        }

        // 2. 构造 MemoryAtom 并生成 Embedding
        var atoms = new ArrayList<MemoryAtom>();
        for (var raw : extraction.atoms()) {
            var atom = new MemoryAtom();
            atom.setUserId(userId);
            atom.setScope(raw.scope() != null ? raw.scope() : "long_term");
            atom.setContent(raw.content());
            atom.setEmbedding(embeddingService.embed(raw.content()));
            atom.setEventTime(eventTime != null ? eventTime : Instant.now());
            atom.setTags(raw.tags());
            atom.setWeight(0.5);
            atoms.add(atom);
        }

        // 3. 编码决策（ADD/UPDATE/DELETE/NONE）
        var decisions = deduplicationService.encodeDecisions(userId, atoms);

        // 4. 收集实际写入的原子（ADD 和 UPDATE 的结果）
        var storedAtoms =
                decisions.stream()
                        .filter(
                                d ->
                                        d.action() == MemoryDeduplicationService.Action.ADD
                                                || d.action()
                                                        == MemoryDeduplicationService.Action.UPDATE)
                        .map(
                                d -> {
                                    var a = new MemoryAtom();
                                    a.setId(d.atomId());
                                    a.setContent(d.content());
                                    return a;
                                })
                        .toList();

        // 5. 存储关系（仅对新增的原子建立关系）
        var addedAtoms =
                decisions.stream()
                        .filter(d -> d.action() == MemoryDeduplicationService.Action.ADD)
                        .toList();
        if (addedAtoms.size() > 1 && extraction.relations() != null) {
            for (var rel : extraction.relations()) {
                if (rel.sourceIndex() < addedAtoms.size()
                        && rel.targetIndex() < addedAtoms.size()) {
                    var relation = new MemoryRelation();
                    relation.setSourceId(addedAtoms.get(rel.sourceIndex()).atomId());
                    relation.setTargetId(addedAtoms.get(rel.targetIndex()).atomId());
                    relation.setRelationType(rel.type());
                    relation.setWeight(rel.weight());
                    atomEngine.addRelation(relation);
                }
            }
        }

        log.debug(
                "记忆编码完成: {} 条决策（ADD={}, UPDATE={}, DELETE={}, NONE={}）",
                decisions.size(),
                decisions.stream()
                        .filter(d -> d.action() == MemoryDeduplicationService.Action.ADD)
                        .count(),
                decisions.stream()
                        .filter(d -> d.action() == MemoryDeduplicationService.Action.UPDATE)
                        .count(),
                decisions.stream()
                        .filter(d -> d.action() == MemoryDeduplicationService.Action.DELETE)
                        .count(),
                decisions.stream()
                        .filter(d -> d.action() == MemoryDeduplicationService.Action.NONE)
                        .count());

        return storedAtoms;
    }

    private ExtractionResult callLlmExtract(String text) {
        try {
            var messages =
                    List.of(
                            (org.springframework.ai.chat.messages.Message)
                                    new SystemMessage(EXTRACTION_PROMPT),
                            (org.springframework.ai.chat.messages.Message) new UserMessage(text));
            var response = chatService.call(messages, "memory_extraction", null);
            var content = response.getResult().getOutput().getText();
            return parseExtraction(content);
        } catch (Exception e) {
            log.warn("记忆抽取 LLM 调用失败: {}", e.getMessage());
            return null;
        }
    }

    private ExtractionResult parseExtraction(String json) {
        try {
            // 提取 JSON 部分（LLM 可能包裹在 markdown code block 中）
            var cleaned =
                    json.contains("{")
                            ? json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1)
                            : json;
            var map = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});

            var atomsRaw =
                    objectMapper.convertValue(
                            map.get("atoms"), new TypeReference<List<Map<String, Object>>>() {});
            var relationsRaw =
                    objectMapper.convertValue(
                            map.get("relations"),
                            new TypeReference<List<Map<String, Object>>>() {});

            var atoms =
                    atomsRaw != null
                            ? atomsRaw.stream()
                                    .map(
                                            m ->
                                                    new RawAtom(
                                                            (String) m.get("content"),
                                                            m.get("tags") instanceof List<?> tags
                                                                    ? tags.stream()
                                                                            .map(Object::toString)
                                                                            .toList()
                                                                    : List.<String>of(),
                                                            (String) m.get("scope")))
                                    .toList()
                            : List.<RawAtom>of();

            var relations =
                    relationsRaw != null
                            ? relationsRaw.stream()
                                    .map(
                                            m ->
                                                    new RawRelation(
                                                            ((Number) m.get("source_index"))
                                                                    .intValue(),
                                                            ((Number) m.get("target_index"))
                                                                    .intValue(),
                                                            (String) m.get("type"),
                                                            m.get("weight") instanceof Number n
                                                                    ? n.doubleValue()
                                                                    : 1.0))
                                    .toList()
                            : List.<RawRelation>of();

            return new ExtractionResult(atoms, relations);
        } catch (Exception e) {
            log.warn("记忆抽取结果解析失败: {}", e.getMessage());
            return null;
        }
    }

    private record RawAtom(String content, List<String> tags, String scope) {}

    private record RawRelation(int sourceIndex, int targetIndex, String type, double weight) {}

    private record ExtractionResult(List<RawAtom> atoms, List<RawRelation> relations) {}
}
