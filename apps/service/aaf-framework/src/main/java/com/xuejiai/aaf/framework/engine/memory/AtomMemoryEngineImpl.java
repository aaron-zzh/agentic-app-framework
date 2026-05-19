/**
 * 原子记忆引擎实现。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** AtomMemoryEngine 实现：同步命令式 + Virtual Threads 并行。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtomMemoryEngineImpl implements AtomMemoryEngine {

    private final MemoryAtomRepository atomRepository;
    private final MemoryRelationRepository relationRepository;
    private final BundleSearchService bundleSearch;
    private final TimeDecayStrategy timeDecay;

    @Override
    @Transactional
    public MemoryAtom store(MemoryAtom atom) {
        return atomRepository.save(atom);
    }

    @Override
    @Transactional
    public List<MemoryAtom> storeBatch(List<MemoryAtom> atoms) {
        return atomRepository.saveAll(atoms);
    }

    @Override
    @Transactional
    public MemoryRelation addRelation(MemoryRelation relation) {
        return relationRepository.save(relation);
    }

    @Override
    public List<MemoryAtom> searchByVector(Long userId, float[] queryVec, int topK) {
        var vecStr = toVectorString(queryVec);
        return atomRepository.searchByVector(userId, vecStr, topK);
    }

    @Override
    public List<MemoryAtom> searchByTime(Long userId, Instant start, Instant end) {
        return atomRepository.findByTimeRange(userId, start, end);
    }

    @Override
    public List<MemoryAtom> searchHybrid(HybridQuery query) {
        var now = Instant.now();

        // 并行执行向量检索和时间检索（虚拟线程）
        List<MemoryAtom> vectorResults;
        List<MemoryAtom> timeResults;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<MemoryAtom>> vectorFuture = query.queryEmbedding() != null
                ? executor.submit(() -> searchByVector(query.userId(), query.queryEmbedding(), query.topK() * 3))
                : executor.submit(() -> List.<MemoryAtom>of());

            Future<List<MemoryAtom>> timeFuture = (query.timeStart() != null && query.timeEnd() != null)
                ? executor.submit(() -> searchByTime(query.userId(), query.timeStart(), query.timeEnd()))
                : executor.submit(() -> List.<MemoryAtom>of());

            vectorResults = vectorFuture.get();
            timeResults = timeFuture.get();
        } catch (Exception e) {
            log.warn("混合检索并行执行失败，降级为串行: {}", e.getMessage());
            vectorResults = query.queryEmbedding() != null
                ? searchByVector(query.userId(), query.queryEmbedding(), query.topK() * 3)
                : List.of();
            timeResults = (query.timeStart() != null && query.timeEnd() != null)
                ? searchByTime(query.userId(), query.timeStart(), query.timeEnd())
                : List.of();
        }

        // 合并去重 + 时间衰减评分
        var merged = new LinkedHashMap<UUID, MemoryAtom>();
        vectorResults.forEach(a -> merged.putIfAbsent(a.getId(), a));
        timeResults.forEach(a -> merged.putIfAbsent(a.getId(), a));

        // 标签过滤
        var filtered = merged.values().stream()
            .filter(a -> matchTags(a, query.tags()))
            .toList();

        // 按时间衰减分数排序
        var scored = filtered.stream()
            .sorted(Comparator.comparingDouble(
                (MemoryAtom a) -> a.getWeight() * timeDecay.score(a.getEventTime(), now, query.queryTime())
            ).reversed())
            .limit(query.topK())
            .toList();

        // 记录访问
        if (!scored.isEmpty()) {
            var ids = scored.stream().map(MemoryAtom::getId).toList();
            atomRepository.recordAccess(ids, now);
        }

        return scored;
    }

    @Override
    public List<MemoryBundle> searchBundles(Long userId, float[] queryVec, int topK, Instant queryTime) {
        return bundleSearch.search(userId, queryVec, topK, queryTime);
    }

    @Override
    @Transactional
    public void invalidate(List<UUID> atomIds) {
        if (!atomIds.isEmpty()) {
            atomRepository.invalidate(atomIds, Instant.now());
        }
    }

    @Override
    @Transactional
    public void updateWeight(UUID atomId, double weight) {
        atomRepository.updateWeight(atomId, weight);
    }

    @Override
    @Transactional
    public void delete(List<UUID> atomIds) {
        if (!atomIds.isEmpty()) {
            atomRepository.deleteAllById(atomIds);
        }
    }

    private boolean matchTags(MemoryAtom atom, List<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) return true;
        if (atom.getTags() == null || atom.getTags().isEmpty()) return false;
        return atom.getTags().stream().anyMatch(requiredTags::contains);
    }

    private String toVectorString(float[] vec) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
