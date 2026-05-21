/**
 * 程序化记忆服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** 程序化记忆：经验蒸馏、SOP 记忆、技能记忆。 */
@Service
@RequiredArgsConstructor
public class ProceduralMemoryService {

    private final ProceduralMemoryRepository repository;

    /** 蒸馏经验：将执行结果转化为程序化记忆 */
    @Transactional
    public ProceduralMemory distill(Long userId, String taskType, String title, String content) {
        var memory = new ProceduralMemory();
        memory.setUserId(userId);
        memory.setTaskType(taskType);
        memory.setTitle(title);
        memory.setContent(content);
        return repository.save(memory);
    }

    /** 按任务类型检索经验 */
    public List<ProceduralMemory> findByTaskType(String taskType, Long userId) {
        return repository.findByTaskType(taskType, userId);
    }

    /** 记录使用并反馈成功/失败 */
    @Transactional
    public void recordUsage(Long memoryId, boolean success) {
        repository
                .findById(memoryId)
                .ifPresent(
                        m -> {
                            m.setUseCount(m.getUseCount() + 1);
                            if (success) {
                                m.setSuccessCount(m.getSuccessCount() + 1);
                            }
                            // 更新质量评分
                            m.setQualityScore((double) m.getSuccessCount() / m.getUseCount());
                            repository.save(m);
                        });
    }
}
