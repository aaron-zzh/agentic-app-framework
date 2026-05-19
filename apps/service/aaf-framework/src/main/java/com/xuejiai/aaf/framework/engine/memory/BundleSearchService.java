/**
 * Bundle Search 服务——图路由记忆检索（借鉴 M-FLOW）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bundle Search：不是找 K 个最相似的孤立片段，而是找 K 组"证据链"。
 * 算法：向量检索候选 → 图谱扩展邻居 → Bundle 评分 → 去重 → Top-K。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BundleSearchService {

    private static final int CANDIDATE_MULTIPLIER = 5; // 候选数 = topK × 5
    private static final int MAX_HOPS = 2; // 最大扩展跳数
    private static final double OVERLAP_THRESHOLD = 0.5; // Bundle 重叠阈值

    private final MemoryAtomRepository atomRepository;
    private final MemoryRelationRepository relationRepository;
    private final TimeDecayStrategy timeDecay;

    /**
     * 执行 Bundle Search。
     *
     * @param userId 用户 ID
     * @param queryVec 查询向量
     * @param topK 返回 Bundle 数量
     * @param queryTime 查询提及的时间点
     * @return 排序后的 Bundle 列表
     */
    public List<MemoryBundle> search(Long userId, float[] queryVec, int topK, Instant queryTime) {
        var now = Instant.now();
        int candidateCount = topK * CANDIDATE_MULTIPLIER;

        // 1. 向量检索候选原子
        var vecStr = toVectorString(queryVec);
        var candidates = atomRepository.searchByVector(userId, vecStr, candidateCount);
        if (candidates.isEmpty()) return List.of();

        // 2. 收集候选原子 ID
        var candidateIds = candidates.stream().map(MemoryAtom::getId).toList();

        // 3. 查找候选原子的关系（1-2 跳邻居）
        var relations = relationRepository.findByAtomIds(candidateIds);

        // 4. 扩展邻居 ID
        var neighborIds = new HashSet<>(candidateIds);
        for (var rel : relations) {
            neighborIds.add(rel.getSourceId());
            neighborIds.add(rel.getTargetId());
        }

        // 5. 加载邻居原子
        var allAtoms = atomRepository.findAllById(neighborIds.stream().toList());
        var atomMap = allAtoms.stream().collect(Collectors.toMap(MemoryAtom::getId, a -> a));

        // 6. 为每个候选原子构建 Bundle
        var bundles = new ArrayList<MemoryBundle>();
        for (var seed : candidates) {
            var bundle = buildBundle(seed, relations, atomMap, now, queryTime);
            bundles.add(bundle);
        }

        // 7. 按分数排序 + 去重
        bundles.sort(Comparator.comparingDouble(MemoryBundle::score).reversed());
        var deduplicated = deduplicateBundles(bundles, topK);

        // 8. 记录访问
        var accessedIds = deduplicated.stream()
            .flatMap(b -> b.atoms().stream())
            .map(MemoryAtom::getId)
            .distinct()
            .toList();
        if (!accessedIds.isEmpty()) {
            atomRepository.recordAccess(accessedIds, now);
        }

        return deduplicated;
    }

    private MemoryBundle buildBundle(
        MemoryAtom seed,
        List<MemoryRelation> allRelations,
        Map<UUID, MemoryAtom> atomMap,
        Instant now,
        Instant queryTime
    ) {
        // 收集与 seed 相关的关系和原子
        var bundleAtoms = new ArrayList<MemoryAtom>();
        var bundleRelations = new ArrayList<MemoryRelation>();
        bundleAtoms.add(seed);

        for (var rel : allRelations) {
            if (rel.getSourceId().equals(seed.getId()) || rel.getTargetId().equals(seed.getId())) {
                bundleRelations.add(rel);
                var neighborId = rel.getSourceId().equals(seed.getId())
                    ? rel.getTargetId() : rel.getSourceId();
                var neighbor = atomMap.get(neighborId);
                if (neighbor != null && !bundleAtoms.contains(neighbor)) {
                    bundleAtoms.add(neighbor);
                }
            }
        }

        // 计算 Bundle 分数 = Σ(原子权重 × 关系权重 × 时间衰减)
        double score = 0.0;
        for (var atom : bundleAtoms) {
            double atomScore = atom.getWeight()
                * timeDecay.score(atom.getEventTime(), now, queryTime);
            score += atomScore;
        }
        for (var rel : bundleRelations) {
            score *= (1.0 + rel.getWeight() * 0.1); // 关系加成
        }

        return new MemoryBundle(bundleAtoms, bundleRelations, score);
    }

    /** 去重：Bundle 间重叠原子 > 50% 则保留分数高的 */
    private List<MemoryBundle> deduplicateBundles(List<MemoryBundle> sorted, int topK) {
        var result = new ArrayList<MemoryBundle>();
        for (var bundle : sorted) {
            if (result.size() >= topK) break;
            boolean overlaps = result.stream().anyMatch(existing -> overlapRatio(existing, bundle) > OVERLAP_THRESHOLD);
            if (!overlaps) {
                result.add(bundle);
            }
        }
        return result;
    }

    private double overlapRatio(MemoryBundle a, MemoryBundle b) {
        var idsA = a.atoms().stream().map(MemoryAtom::getId).collect(Collectors.toSet());
        var idsB = b.atoms().stream().map(MemoryAtom::getId).collect(Collectors.toSet());
        long common = idsA.stream().filter(idsB::contains).count();
        return (double) common / Math.min(idsA.size(), idsB.size());
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
