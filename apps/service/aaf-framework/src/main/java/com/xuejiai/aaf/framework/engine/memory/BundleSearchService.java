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

/** Bundle Search：不是找 K 个最相似的孤立片段，而是找 K 组"证据链"。 算法：向量检索候选 → 图谱扩展邻居 → Bundle 评分 → 去重 → Top-K。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BundleSearchService {

    private static final int CANDIDATE_MULTIPLIER = 5; // 候选数 = topK × 5
    private static final double OVERLAP_THRESHOLD = 0.5; // Bundle 重叠阈值
    private static final double DIRECT_HIT_PENALTY = 0.25; // 直接命中 seed 的路径惩罚
    private static final double HOP_PENALTY = 0.1; // 每跳惩罚
    private static final double EDGE_MISS_COST = 0.8; // 边无向量/向量缺失时的兜底代价

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

        // 6. 自适应边置信度（优化点5）：统计候选相关边与 query 的平均距离。
        //    边整体越贴近 query（可靠）→ 因子越小，边路径代价更低、影响放大；
        //    边噪声大 → 因子变大，抑制边路径，回退到节点距离。
        double avgEdgeDist =
                relations.stream()
                        .filter(r -> r.getEdgeEmbedding() != null)
                        .mapToDouble(r -> vecDistance(queryVec, r.getEdgeEmbedding()))
                        .average()
                        .orElse(1.0);
        double edgeWeightFactor = Math.max(0.3, Math.min(1.5, avgEdgeDist * 1.5));

        // 7. 为每个候选原子构建 Bundle（代价传播 + 取最小路径）
        var bundles = new ArrayList<MemoryBundle>();
        for (var seed : candidates) {
            var bundle =
                    buildBundle(
                            seed, relations, atomMap, queryVec, edgeWeightFactor, now, queryTime);
            bundles.add(bundle);
        }

        // 8. 按分数排序 + 去重
        bundles.sort(Comparator.comparingDouble(MemoryBundle::score).reversed());
        var deduplicated = deduplicateBundles(bundles, topK);

        // 9. 记录访问
        var accessedIds =
                deduplicated.stream()
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
            float[] queryVec,
            double edgeWeightFactor,
            Instant now,
            Instant queryTime) {
        // 收集与 seed 相关的关系和原子
        var bundleAtoms = new ArrayList<MemoryAtom>();
        var bundleRelations = new ArrayList<MemoryRelation>();
        bundleAtoms.add(seed);

        for (var rel : allRelations) {
            if (rel.getSourceId().equals(seed.getId()) || rel.getTargetId().equals(seed.getId())) {
                bundleRelations.add(rel);
                var neighborId =
                        rel.getSourceId().equals(seed.getId())
                                ? rel.getTargetId()
                                : rel.getSourceId();
                var neighbor = atomMap.get(neighborId);
                if (neighbor != null && !bundleAtoms.contains(neighbor)) {
                    bundleAtoms.add(neighbor);
                }
            }
        }

        // 代价传播打分（优化点2）：
        // - 直接命中 seed 的路径加惩罚，偏好从精确锚点经边多跳过来（避免宽泛摘要霸榜）；
        // - 每条"邻居→边→seed"路径累加（邻居距离 + 自适应边代价 + 跳惩罚）；
        // - 取所有路径的最小代价（min-not-average：一条强证据链足以证明相关）。
        double seedDist = vecDistance(queryVec, seed.getEmbedding());
        double bestCost = seedDist + DIRECT_HIT_PENALTY;

        for (var rel : bundleRelations) {
            var neighborId =
                    rel.getSourceId().equals(seed.getId())
                            ? rel.getTargetId()
                            : rel.getSourceId();
            var neighbor = atomMap.get(neighborId);
            double neighborDist =
                    neighbor != null
                            ? vecDistance(queryVec, neighbor.getEmbedding())
                            : EDGE_MISS_COST;
            double edgeDist =
                    rel.getEdgeEmbedding() != null
                            ? vecDistance(queryVec, rel.getEdgeEmbedding())
                            : EDGE_MISS_COST;
            double pathCost = neighborDist + edgeWeightFactor * edgeDist + HOP_PENALTY;
            bestCost = Math.min(bestCost, pathCost);
        }

        // 最小代价 → 分数（代价越小分数越高），时间衰减作为次要因子
        double timeBoost = timeDecay.score(seed.getEventTime(), now, queryTime);
        double score = (1.0 / (1.0 + bestCost)) * (0.6 + 0.4 * timeBoost);

        return new MemoryBundle(bundleAtoms, bundleRelations, score);
    }

    /** 余弦距离 = 1 - cos，范围 [0,2]，0 表示完全一致。向量缺失或维度不符返回兜底代价。 */
    private static double vecDistance(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return EDGE_MISS_COST;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return EDGE_MISS_COST;
        return 1.0 - dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** 去重：Bundle 间重叠原子 > 50% 则保留分数高的 */
    private List<MemoryBundle> deduplicateBundles(List<MemoryBundle> sorted, int topK) {
        var result = new ArrayList<MemoryBundle>();
        for (var bundle : sorted) {
            if (result.size() >= topK) break;
            boolean overlaps =
                    result.stream()
                            .anyMatch(
                                    existing -> overlapRatio(existing, bundle) > OVERLAP_THRESHOLD);
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
