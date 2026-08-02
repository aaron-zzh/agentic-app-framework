package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Hit;

/** 置信度评分服务 — 综合检索相关性和引用覆盖率 */
@Service
public class ConfidenceScorer {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    /** 计算 RAG 输出置信度（0-1） */
    public double score(String answer, List<Hit> sources) {
        if (sources == null || sources.isEmpty()) return 0.0;

        // 检索相关性：源文档平均分数
        double relevance = sources.stream().mapToDouble(Hit::score).average().orElse(0.0);

        // 引用覆盖率：答案中引用数 / 源文档数
        var matcher = CITATION_PATTERN.matcher(answer != null ? answer : "");
        long citationCount = matcher.results().map(m -> m.group(1)).distinct().count();
        double coverage = Math.min(1.0, (double) citationCount / sources.size());

        // 综合分
        return Math.min(1.0, 0.6 * relevance + 0.4 * coverage);
    }
}
