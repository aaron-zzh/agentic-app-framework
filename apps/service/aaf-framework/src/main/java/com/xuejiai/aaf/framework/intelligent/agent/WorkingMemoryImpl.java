/**
 * 工作记忆实现——从检索结果中选取焦点项。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.cognition.retrieval.UnifiedRetrievalService;
import com.xuejiai.aaf.framework.intelligent.cognition.retrieval.UnifiedRetrievalService.RetrievalRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 工作记忆实现：从融合检索结果中选取最相关的 N 项作为 Agent 焦点。 有限容量（3-7 项），任务结束释放。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingMemoryImpl implements WorkingMemory {

    private final UnifiedRetrievalService retrievalService;
    private final ConcurrentHashMap<String, List<FocusItem>> store = new ConcurrentHashMap<>();

    @Override
    public void focus(String agentId, String query, int maxItems) {
        // 从融合检索获取结果，转为焦点项
        var result =
                retrievalService.retrieve(new RetrievalRequest(query, null, null, maxItems * 2));

        var items = new ArrayList<FocusItem>();

        // 融合结果 → 焦点项
        for (var fused : result.fused()) {
            items.add(new FocusItem(fused.source(), fused.content(), fused.score()));
        }

        // 按相关性排序，截断到 maxItems
        items.sort(Comparator.comparingDouble(FocusItem::relevance).reversed());
        var focused = items.stream().limit(maxItems).toList();

        store.put(agentId, new ArrayList<>(focused));
        log.debug("Agent [{}] 工作记忆聚焦 {} 项", agentId, focused.size());
    }

    @Override
    public List<FocusItem> getFocus(String agentId) {
        return store.getOrDefault(agentId, List.of());
    }

    @Override
    public void append(String agentId, FocusItem item) {
        store.computeIfAbsent(agentId, k -> new ArrayList<>()).add(item);
    }

    @Override
    public void release(String agentId) {
        store.remove(agentId);
    }
}
