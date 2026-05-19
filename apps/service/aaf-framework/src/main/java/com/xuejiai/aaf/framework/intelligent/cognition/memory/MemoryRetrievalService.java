/**
 * 记忆融合检索服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 多源融合检索：从短期/长期/图谱/程序化记忆中检索相关内容，
 * 按相关性排序，应用记忆衰减策略。
 */
@Service
@RequiredArgsConstructor
public class MemoryRetrievalService {

    private final ShortTermMemoryService shortTermMemory;
    private final LongTermMemoryService longTermMemory;
    private final GraphMemoryService graphMemory;
    private final ProceduralMemoryService proceduralMemory;

    /** 融合检索：从所有记忆源获取相关内容 */
    public MemoryContext retrieve(Long userId, String conversationId, String query) {
        var context = new MemoryContext();

        // 短期记忆：当前对话上下文
        if (conversationId != null) {
            context.setRecentMessages(shortTermMemory.getContext(conversationId, 10));
        }

        // 长期记忆：按重要性
        context.setLongTermMemories(longTermMemory.recall(userId));

        // 图谱记忆：关联实体
        if (query != null) {
            context.setRelatedEntities(graphMemory.search(userId, query));
        }

        return context;
    }

    /** 按任务类型检索程序化记忆 */
    public List<ProceduralMemory> retrieveProcedural(Long userId, String taskType) {
        return proceduralMemory.findByTaskType(taskType, userId);
    }

    /** 记忆上下文（聚合结果） */
    @lombok.Getter
    @lombok.Setter
    public static class MemoryContext {
        private List<MemoryMessage> recentMessages = new ArrayList<>();
        private List<LongTermMemory> longTermMemories = new ArrayList<>();
        private List<GraphMemoryNode> relatedEntities = new ArrayList<>();
    }
}
