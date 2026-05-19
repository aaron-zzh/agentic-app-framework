/**
 * 记忆写入决策服务——编码阶段的智能决策（借鉴 Mem0 + 认知心理学编码理论）。
 *
 * <p>认知心理学对齐：人类记忆编码不是简单的"存入"，而是经过注意筛选、
 * 与已有记忆比对、决定新建/更新/遗忘的主动过程。本服务模拟这一过程。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.intelligent.ai.ResilientChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 记忆编码决策：对每条新事实，LLM 决定 ADD / UPDATE / DELETE / NONE。
 *
 * <p>流程（对齐认知心理学编码过程）：
 * <ol>
 *   <li>注意筛选：MemoryExtractionService 已完成（只提取有价值的事实）</li>
 *   <li>激活已有记忆：向量检索相关已有记忆</li>
 *   <li>编码决策：LLM 比对新旧，决定操作类型</li>
 *   <li>执行：新增/更新/删除/跳过</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryDeduplicationService {

    private static final int CANDIDATE_COUNT = 5;

    private final AtomMemoryEngine atomEngine;
    private final EmbeddingService embeddingService;
    private final ResilientChatService chatService;
    private final ObjectMapper objectMapper;

    private static final String DECISION_PROMPT = """
        你是记忆管理专家。给定一条新事实和已有记忆列表，决定如何处理。
        
        新事实: %s
        
        已有记忆:
        %s
        
        对新事实做出决策（只选一个）：
        - ADD: 全新信息，已有记忆中没有相关内容
        - UPDATE: 已有记忆需要更新（信息变化、补充细节），指定要更新的记忆 ID
        - DELETE: 新事实表明某条旧记忆已过时/错误，指定要删除的记忆 ID
        - NONE: 已有记忆中已包含相同信息，无需操作
        
        返回 JSON：
        {"action": "ADD|UPDATE|DELETE|NONE", "target_id": "要操作的已有记忆ID（ADD/NONE时为null）", "content": "最终记忆内容（ADD/UPDATE时填写）"}
        """;

    /**
     * 对一批新提取的事实执行编码决策。
     *
     * @param userId 用户 ID
     * @param newAtoms 新提取的记忆原子（已有 content 和 embedding）
     * @return 决策执行结果
     */
    public List<DecisionResult> encodeDecisions(Long userId, List<MemoryAtom> newAtoms) {
        var results = new ArrayList<DecisionResult>();

        for (var atom : newAtoms) {
            if (atom.getEmbedding() == null) {
                atom.setEmbedding(embeddingService.embed(atom.getContent()));
            }

            // 激活已有记忆（向量检索相关候选）
            var candidates = atomEngine.searchByVector(userId, atom.getEmbedding(), CANDIDATE_COUNT);

            if (candidates.isEmpty()) {
                // 无已有记忆，直接 ADD
                var stored = atomEngine.store(atom);
                results.add(new DecisionResult(Action.ADD, stored.getId(), stored.getContent()));
                continue;
            }

            // LLM 编码决策
            var decision = callLlmDecision(atom.getContent(), candidates);
            results.add(executeDecision(decision, atom, candidates));
        }

        return results;
    }

    /**
     * 单条记忆的去重检查（简化版，兼容旧接口）。
     */
    public MemoryAtom deduplicateOrPass(MemoryAtom atom) {
        if (atom.getEmbedding() == null) {
            atom.setEmbedding(embeddingService.embed(atom.getContent()));
        }

        var candidates = atomEngine.searchByVector(atom.getUserId(), atom.getEmbedding(), CANDIDATE_COUNT);
        if (candidates.isEmpty()) return atom;

        var decision = callLlmDecision(atom.getContent(), candidates);
        if (decision.action() == Action.ADD) return atom;
        if (decision.action() == Action.UPDATE) {
            // 更新已有记忆
            var target = candidates.stream()
                .filter(c -> c.getId().toString().equals(decision.targetId()))
                .findFirst()
                .orElse(candidates.getFirst());
            target.setContent(decision.content() != null ? decision.content() : atom.getContent());
            target.setEmbedding(embeddingService.embed(target.getContent()));
            target.setWeight(Math.min(1.0, target.getWeight() + 0.1));
            atomEngine.store(target);
            return null; // 已合并
        }
        // NONE 或 DELETE
        return null;
    }

    private Decision callLlmDecision(String newContent, List<MemoryAtom> candidates) {
        try {
            var existingText = new StringBuilder();
            for (int i = 0; i < candidates.size(); i++) {
                existingText.append("[%s] %s\n".formatted(
                    candidates.get(i).getId().toString(), candidates.get(i).getContent()));
            }

            var prompt = DECISION_PROMPT.formatted(newContent, existingText.toString());
            var messages = List.of(
                (org.springframework.ai.chat.messages.Message) new SystemMessage("只返回 JSON，不要其他内容。"),
                (org.springframework.ai.chat.messages.Message) new UserMessage(prompt)
            );
            var response = chatService.call(messages, "memory_decision", null);
            var text = response.getResult().getOutput().getText().trim();
            return parseDecision(text);
        } catch (Exception e) {
            log.warn("记忆决策 LLM 调用失败，默认 ADD: {}", e.getMessage());
            return new Decision(Action.ADD, null, newContent);
        }
    }

    private Decision parseDecision(String json) {
        try {
            var cleaned = json.contains("{") ? json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1) : json;
            var map = objectMapper.readValue(cleaned, new TypeReference<java.util.Map<String, Object>>() {});
            var action = Action.valueOf(((String) map.getOrDefault("action", "ADD")).toUpperCase());
            var targetId = map.get("target_id") != null ? map.get("target_id").toString() : null;
            var content = (String) map.get("content");
            return new Decision(action, targetId, content);
        } catch (Exception e) {
            log.warn("记忆决策解析失败，默认 ADD: {}", e.getMessage());
            return new Decision(Action.ADD, null, null);
        }
    }

    private DecisionResult executeDecision(Decision decision, MemoryAtom newAtom, List<MemoryAtom> candidates) {
        return switch (decision.action()) {
            case ADD -> {
                var stored = atomEngine.store(newAtom);
                yield new DecisionResult(Action.ADD, stored.getId(), stored.getContent());
            }
            case UPDATE -> {
                var target = findTarget(decision.targetId(), candidates);
                if (target != null) {
                    target.setContent(decision.content() != null ? decision.content() : newAtom.getContent());
                    target.setEmbedding(embeddingService.embed(target.getContent()));
                    target.setWeight(Math.min(1.0, target.getWeight() + 0.1)); // 强化
                    atomEngine.store(target);
                    yield new DecisionResult(Action.UPDATE, target.getId(), target.getContent());
                } else {
                    var stored = atomEngine.store(newAtom);
                    yield new DecisionResult(Action.ADD, stored.getId(), stored.getContent());
                }
            }
            case DELETE -> {
                var target = findTarget(decision.targetId(), candidates);
                if (target != null) {
                    atomEngine.invalidate(List.of(target.getId()));
                    yield new DecisionResult(Action.DELETE, target.getId(), target.getContent());
                } else {
                    yield new DecisionResult(Action.NONE, null, null);
                }
            }
            case NONE -> new DecisionResult(Action.NONE, null, newAtom.getContent());
        };
    }

    private MemoryAtom findTarget(String targetId, List<MemoryAtom> candidates) {
        if (targetId == null || candidates.isEmpty()) return null;
        return candidates.stream()
            .filter(c -> c.getId().toString().equals(targetId))
            .findFirst()
            .orElse(null);
    }

    /** 记忆操作类型（对齐认知心理学：编码/更新/遗忘/忽略） */
    public enum Action { ADD, UPDATE, DELETE, NONE }

    /** LLM 决策结果 */
    private record Decision(Action action, String targetId, String content) {}

    /** 执行结果 */
    public record DecisionResult(Action action, UUID atomId, String content) {}
}
