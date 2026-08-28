/**
 * 检索结果重排服务（Reranker）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.intelligent.ai.rerank.RerankService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 记忆重排服务，支持两种模式（按场景分流）。
 *
 * <ul>
 *   <li>{@link Mode#LIGHTWEIGHT}（默认）：纯计算（向量召回序 + 词法重叠 + 价值权重），用于对话热路径，零延迟零成本。
 *   <li>{@link Mode#RERANK_MODEL}：委托专用重排模型（{@link RerankService}，如 qwen3-rerank 的 cross-encoder
 *       rerank API）， 仅用于高价值/非延迟敏感场景（显式 /recall、批处理）。即便选模型也先经廉价门控，模型不可用或失败时自动降级回轻量。
 * </ul>
 *
 * <p>判断原则：路径决定"能不能用重排模型"（调用方声明），门控决定"这次值不值得用"。重排是独立能力， 走专用 {@link RerankService}（对齐
 * CAP_RERANK=qwen3-rerank），而非为 chat 设计的能力路由链。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRerankerService {

    /** 重排模式。 */
    public enum Mode {
        LIGHTWEIGHT,
        RERANK_MODEL
    }

    /** 重排门控：候选少于此数不值得调模型。 */
    private static final int MIN_FOR_MODEL = 2;

    /** 重排门控：候选超过此数先用轻量裁剪，控成本。 */
    private static final int MAX_FOR_MODEL = 20;

    /** 专用重排模型服务（可选 Bean：未配置 dashscope key 时不存在，自动降级轻量）。 */
    private final ObjectProvider<RerankService> rerankServiceProvider;

    /** 默认轻量重排（热路径调用方使用，签名不变）。 */
    public List<MemoryAtom> rerank(String query, List<MemoryAtom> candidates, int topK) {
        if (candidates == null || candidates.size() <= 1) return candidates;
        return lightweight(query, candidates, topK);
    }

    /**
     * 按模式重排。
     *
     * @param mode LIGHTWEIGHT（纯计算）/ RERANK_MODEL（专用重排模型，带门控 + 降级）
     */
    public List<MemoryAtom> rerank(String query, List<MemoryAtom> candidates, int topK, Mode mode) {
        if (candidates == null || candidates.size() <= 1) return candidates;

        if (mode == Mode.RERANK_MODEL && candidates.size() >= MIN_FOR_MODEL) {
            var svc = rerankServiceProvider.getIfAvailable();
            if (svc != null) {
                var pool =
                        candidates.size() > MAX_FOR_MODEL
                                ? lightweight(query, candidates, MAX_FOR_MODEL)
                                : candidates;
                var ranked = modelRerank(svc, query, pool, topK);
                if (ranked != null) return ranked; // 失败/不可用则降级轻量
            }
        }
        return lightweight(query, candidates, topK);
    }

    // ===== 专用重排模型（按需） =====

    private List<MemoryAtom> modelRerank(
            RerankService svc, String query, List<MemoryAtom> candidates, int topK) {
        try {
            var docs = candidates.stream().map(MemoryAtom::getContent).toList();
            // RankedDocument.index 指回 candidates，按相关性降序
            return svc.rerank(query, docs, topK).stream()
                    .map(RerankService.RankedDocument::index)
                    .filter(i -> i >= 0 && i < candidates.size())
                    .map(candidates::get)
                    .toList();
        } catch (Exception e) {
            log.warn("专用重排失败，降级轻量: {}", e.getMessage());
            return null;
        }
    }

    // ===== 轻量重排（纯计算） =====

    private List<MemoryAtom> lightweight(String query, List<MemoryAtom> candidates, int topK) {
        var terms = tokenize(query);
        int n = candidates.size();
        record Scored(MemoryAtom atom, double score) {}
        var scored = new ArrayList<Scored>(n);
        for (int i = 0; i < n; i++) {
            var a = candidates.get(i);
            // 向量召回序作为主序（base），词法重叠 + 价值权重作为轻量加成
            double base = n - i;
            double bonus = lexicalOverlap(terms, a.getContent()) + safeWeight(a);
            scored.add(new Scored(a, base + bonus));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.stream().limit(topK).map(Scored::atom).toList();
    }

    private double safeWeight(MemoryAtom a) {
        return a.getWeight() != null ? a.getWeight() : 0.5;
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.toLowerCase().split("[\\s,.;:!?，。；：！？、]+"))
                .filter(t -> t.length() > 1)
                .collect(Collectors.toSet());
    }

    private double lexicalOverlap(Set<String> queryTerms, String content) {
        if (queryTerms.isEmpty() || content == null) return 0.0;
        var lower = content.toLowerCase();
        long hits = queryTerms.stream().filter(lower::contains).count();
        return (double) hits / queryTerms.size();
    }

    // ===== 通用内容重排（跨源候选，非 MemoryAtom） =====

    /**
     * 对任意内容列表按融合序重排，返回按相关性降序的原始下标。
     *
     * <p>用于 {@code UnifiedRetrievalPort} 融合后的跨源候选重排——候选来自记忆与知识库多个通道，不是单一
     * {@link MemoryAtom} 列表，因此按下标返回而非按对象返回。候选数达门控下限才调用专用模型，模型不可用或失败时降级为原融合序（下标恒等映射）。
     *
     * @param query 查询文本
     * @param contents 融合后的候选内容，按融合序排列
     * @param topK 返回前 K 个下标
     * @return 按相关性降序排列的原始下标；候选数不足或未启用模型时返回原融合序下标
     */
    public List<Integer> rerankContents(String query, List<String> contents, int topK) {
        if (contents == null || contents.size() <= 1) {
            return identityIndexes(contents);
        }
        if (contents.size() < MIN_FOR_MODEL) {
            return identityIndexes(contents);
        }
        var svc = rerankServiceProvider.getIfAvailable();
        if (svc == null) {
            return identityIndexes(contents);
        }
        try {
            return svc.rerank(query, contents, topK).stream()
                    .map(RerankService.RankedDocument::index)
                    .filter(i -> i >= 0 && i < contents.size())
                    .toList();
        } catch (Exception e) {
            log.warn("[混合检索] 跨源候选重排失败，降级为融合序: {}", e.getMessage());
            return identityIndexes(contents);
        }
    }

    private static List<Integer> identityIndexes(List<String> contents) {
        if (contents == null) return List.of();
        return java.util.stream.IntStream.range(0, contents.size()).boxed().toList();
    }
}
