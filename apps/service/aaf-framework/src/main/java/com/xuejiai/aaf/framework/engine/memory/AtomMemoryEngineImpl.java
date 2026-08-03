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
        if (relation.getSourceId().equals(relation.getTargetId())) {
            throw new IllegalArgumentException("记忆关系两端不能相同");
        }
        var atoms =
                atomRepository.findAllById(List.of(relation.getSourceId(), relation.getTargetId()));
        if (atoms.size() != 2) {
            throw new IllegalArgumentException("记忆关系端点不存在");
        }
        if (!Objects.equals(atoms.get(0).getUserId(), atoms.get(1).getUserId())) {
            throw new IllegalArgumentException("不能建立跨用户记忆关系");
        }
        return relationRepository.save(relation);
    }

    @Override
    public List<MemoryAtom> searchByVector(Long userId, float[] queryVec, int topK) {
        return searchByVectorAt(userId, queryVec, topK, Instant.now());
    }

    @Override
    public List<MemoryAtom> searchByTime(Long userId, Instant start, Instant end) {
        return searchByTimeAt(userId, start, end, Instant.now());
    }

    @Override
    @Transactional
    public List<MemoryAtom> searchHybrid(HybridQuery query) {
        var now = Instant.now();
        List<MemoryAtom> vectorResults;
        List<MemoryAtom> timeResults;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<MemoryAtom>> vectorFuture =
                    query.queryEmbedding() != null
                            ? executor.submit(
                                    () ->
                                            searchByVectorAt(
                                                    query.userId(),
                                                    query.queryEmbedding(),
                                                    query.topK() * 3,
                                                    now))
                            : executor.submit(() -> List.<MemoryAtom>of());
            Future<List<MemoryAtom>> timeFuture =
                    query.timeStart() != null && query.timeEnd() != null
                            ? executor.submit(
                                    () ->
                                            searchByTimeAt(
                                                    query.userId(),
                                                    query.timeStart(),
                                                    query.timeEnd(),
                                                    now))
                            : executor.submit(() -> List.<MemoryAtom>of());
            vectorResults = vectorFuture.get();
            timeResults = timeFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("混合检索被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("混合检索执行失败", e);
        }

        var merged = new LinkedHashMap<UUID, MemoryAtom>();
        vectorResults.forEach(atom -> merged.putIfAbsent(atom.getId(), atom));
        timeResults.forEach(atom -> merged.putIfAbsent(atom.getId(), atom));
        var vectorRanks = reciprocalRankScores(vectorResults);
        var timeRanks = reciprocalRankScores(timeResults);

        var scored =
                merged.values().stream()
                        .filter(atom -> matchTags(atom, query.tags()))
                        .sorted(
                                Comparator.comparingDouble(
                                                (MemoryAtom atom) ->
                                                        hybridScore(
                                                                atom,
                                                                vectorRanks,
                                                                timeRanks,
                                                                now,
                                                                query.queryTime()))
                                        .reversed())
                        .limit(query.topK())
                        .toList();

        if (!scored.isEmpty()) {
            atomRepository.recordAccess(scored.stream().map(MemoryAtom::getId).toList(), now);
        }
        return scored;
    }

    @Override
    public List<MemoryBundle> searchBundles(
            Long userId, float[] queryVec, int topK, Instant queryTime) {
        return bundleSearch.search(userId, queryVec, topK, queryTime);
    }

    @Override
    public List<MemoryAtom> searchByScope(Long userId, String scope, int topK) {
        return atomRepository.findCurrentByUserIdAndScope(userId, scope, Instant.now()).stream()
                .sorted(Comparator.comparingDouble(MemoryAtom::getWeight).reversed())
                .limit(topK)
                .toList();
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
    public void recordUsage(UUID atomId, boolean success) {
        atomRepository
                .findById(atomId)
                .ifPresent(
                        atom -> {
                            int useCount = atom.getAccessCount() + 1;
                            atom.setAccessCount(useCount);
                            atom.setLastAccessedAt(Instant.now());
                            var meta =
                                    atom.getMetadata() != null
                                            ? new HashMap<>(atom.getMetadata())
                                            : new HashMap<String, Object>();
                            int successCount =
                                    ((Number) meta.getOrDefault("successCount", 0)).intValue();
                            if (success) successCount++;
                            meta.put("successCount", successCount);
                            atom.setMetadata(meta);
                            atom.setWeight(useCount > 0 ? (double) successCount / useCount : 0.5);
                            atomRepository.save(atom);
                        });
    }

    @Override
    @Transactional
    public void delete(List<UUID> atomIds) {
        if (!atomIds.isEmpty()) {
            atomRepository.deleteAllById(atomIds);
        }
    }

    private List<MemoryAtom> searchByVectorAt(Long userId, float[] queryVec, int topK, Instant at) {
        return atomRepository.searchByVector(userId, toVectorString(queryVec), topK, at);
    }

    private List<MemoryAtom> searchByTimeAt(Long userId, Instant start, Instant end, Instant at) {
        return atomRepository.findByTimeRange(userId, start, end, at);
    }

    private Map<UUID, Double> reciprocalRankScores(List<MemoryAtom> results) {
        var scores = new HashMap<UUID, Double>();
        for (var index = 0; index < results.size(); index++) {
            scores.put(results.get(index).getId(), 1.0 / (60 + index + 1));
        }
        return scores;
    }

    private double hybridScore(
            MemoryAtom atom,
            Map<UUID, Double> vectorRanks,
            Map<UUID, Double> timeRanks,
            Instant now,
            Instant queryTime) {
        var retrievalScore =
                vectorRanks.getOrDefault(atom.getId(), 0.0)
                        + timeRanks.getOrDefault(atom.getId(), 0.0);
        return retrievalScore
                * atom.getWeight()
                * timeDecay.score(atom.getEventTime(), now, queryTime);
    }

    private boolean matchTags(MemoryAtom atom, List<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) return true;
        if (atom.getTags() == null || atom.getTags().length == 0) return false;
        return Arrays.stream(atom.getTags()).anyMatch(requiredTags::contains);
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
