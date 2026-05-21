/**
 * 检索结果重排服务（Reranker）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.util.Comparator;
import java.util.List;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.intelligent.ai.ResilientChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 对检索结果进行 LLM 重排序，根据查询上下文重新评估相关性。 属于 Retrieval Agentic 检索流程的一环。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRerankerService {

    private final ResilientChatService chatService;

    private static final String RERANK_PROMPT =
            """
        你是相关性评分专家。给定一个查询和多条记忆，为每条记忆评估与查询的相关性分数（0.0~1.0）。

        查询: %s

        记忆列表:
        %s

        返回每条记忆的分数，格式为每行一个数字（与记忆顺序对应），例如：
        0.9
        0.3
        0.7
        """;

    /**
     * 对检索结果重排序。
     *
     * @param query 用户查询
     * @param candidates 候选记忆列表
     * @param topK 返回数量
     * @return 重排后的记忆列表
     */
    public List<MemoryAtom> rerank(String query, List<MemoryAtom> candidates, int topK) {
        if (candidates.size() <= 1) return candidates;

        var scores = callLlmRerank(query, candidates);
        if (scores == null || scores.size() != candidates.size()) {
            // LLM 失败，保持原序
            return candidates.stream().limit(topK).toList();
        }

        // 按分数重排
        record Scored(MemoryAtom atom, double score) {}
        var scored = new java.util.ArrayList<Scored>();
        for (int i = 0; i < candidates.size(); i++) {
            scored.add(new Scored(candidates.get(i), scores.get(i)));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        return scored.stream().limit(topK).map(Scored::atom).toList();
    }

    private List<Double> callLlmRerank(String query, List<MemoryAtom> candidates) {
        try {
            var memoriesText = new StringBuilder();
            for (int i = 0; i < candidates.size(); i++) {
                memoriesText.append("[%d] %s\n".formatted(i + 1, candidates.get(i).getContent()));
            }

            var prompt = RERANK_PROMPT.formatted(query, memoriesText.toString());
            var messages =
                    List.of(
                            (org.springframework.ai.chat.messages.Message)
                                    new SystemMessage("只输出分数，每行一个数字。"),
                            (org.springframework.ai.chat.messages.Message) new UserMessage(prompt));
            var response = chatService.call(messages, "memory_rerank", null);
            var text = response.getResult().getOutput().getText().trim();

            return text.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(
                            line -> {
                                try {
                                    return Double.parseDouble(line);
                                } catch (NumberFormatException e) {
                                    return 0.5;
                                }
                            })
                    .toList();
        } catch (Exception e) {
            log.warn("Reranker LLM 调用失败: {}", e.getMessage());
            return null;
        }
    }
}
