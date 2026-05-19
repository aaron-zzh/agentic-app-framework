package com.xuejiai.aaf.framework.engine.knowledge.rag;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 引用溯源服务 — 解析答案中的 [N] 引用标记，映射到源文档
 */
@Service
public class CitationService {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    /**
     * 从答案中提取引用标记，映射到对应的检索结果
     */
    public List<Citation> extractCitations(String answer, List<RagSearchResult> sources) {
        if (answer == null || sources == null || sources.isEmpty()) return List.of();

        var matcher = CITATION_PATTERN.matcher(answer);
        Set<Integer> seen = new LinkedHashSet<>();
        while (matcher.find()) {
            seen.add(Integer.parseInt(matcher.group(1)));
        }

        return seen.stream()
                .filter(idx -> idx >= 1 && idx <= sources.size())
                .map(idx -> {
                    var source = sources.get(idx - 1);
                    return new Citation(
                            idx,
                            source.content(),
                            source.source(),
                            Objects.toString(source.metadata().get("document_id"), null)
                    );
                })
                .toList();
    }
}
