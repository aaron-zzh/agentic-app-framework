package com.xuejiai.aaf.framework.engine.memory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 记忆维护定时任务——衰减清理 + 权重更新。
 *
 * <p>每日凌晨执行：扫描过期/低权重原子，批量失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryMaintenanceTask {

    private final MemoryAtomRepository atomRepository;
    private final AtomMemoryEngine memoryEngine;
    private final TimeDecayStrategy decayStrategy;

    /** 权重低于此阈值的原子将被失效 */
    private static final double DECAY_THRESHOLD = 0.05;

    /** 每日 3:00 执行记忆衰减清理 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void decayAndCleanup() {
        log.info("[MemoryMaintenance] 开始记忆衰减清理");
        var now = Instant.now();
        var cutoff = now.minus(90, ChronoUnit.DAYS);

        // 查找 90 天未访问的原子
        var staleAtoms = atomRepository.findStaleAtoms(cutoff);

        // 计算衰减，低于阈值的批量失效
        var toInvalidate =
                staleAtoms.stream()
                        .filter(
                                atom -> {
                                    double score = decayStrategy.decay(atom.getEventTime(), now);
                                    return score * atom.getWeight() < DECAY_THRESHOLD;
                                })
                        .map(MemoryAtom::getId)
                        .toList();

        if (!toInvalidate.isEmpty()) {
            memoryEngine.invalidate(toInvalidate);
            log.info("[MemoryMaintenance] 失效 {} 条低价值记忆", toInvalidate.size());
        } else {
            log.info("[MemoryMaintenance] 无需清理");
        }
    }
}
