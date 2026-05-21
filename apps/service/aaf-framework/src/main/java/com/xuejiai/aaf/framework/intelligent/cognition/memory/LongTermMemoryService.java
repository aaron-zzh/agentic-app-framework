/**
 * 长期记忆服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** 长期记忆：持久化、提取与压缩、重要性评分、衰减。 */
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private final LongTermMemoryRepository repository;

    /** 存储记忆 */
    @Transactional
    public LongTermMemory store(Long userId, String content, String memoryType, Double importance) {
        var memory = new LongTermMemory();
        memory.setUserId(userId);
        memory.setContent(content);
        memory.setMemoryType(memoryType);
        memory.setImportance(importance != null ? importance : 0.5);
        memory.setEventTime(LocalDateTime.now());
        return repository.save(memory);
    }

    /** 检索记忆（按重要性） */
    public List<LongTermMemory> recall(Long userId) {
        return repository.findByUserIdOrderByImportanceDesc(userId);
    }

    /** 检索最近记忆 */
    public List<LongTermMemory> recallRecent(Long userId) {
        return repository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 记录访问（更新衰减参数） */
    @Transactional
    public void recordAccess(Long memoryId) {
        repository
                .findById(memoryId)
                .ifPresent(
                        m -> {
                            m.setAccessCount(m.getAccessCount() + 1);
                            m.setLastAccessedAt(LocalDateTime.now());
                            // 访问频率提升重要性
                            m.setImportance(Math.min(1.0, m.getImportance() + 0.05));
                            repository.save(m);
                        });
    }

    /** 遗忘：归档低价值记忆 */
    @Transactional
    public int forget(Long userId, double threshold, int daysInactive) {
        var before = LocalDateTime.now().minusDays(daysInactive);
        var lowValue = repository.findLowValueMemories(userId, threshold, before);
        repository.deleteAll(lowValue);
        return lowValue.size();
    }
}
