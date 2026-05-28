package com.xuejiai.aaf.framework.engine.workflow.node;

import java.util.stream.Collectors;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchConfig;
import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库检索节点——工作流中直接检索知识库。
 *
 * <p>流程变量：
 * <ul>
 *   <li>knowledgeBaseId（必填）——知识库 ID</li>
 *   <li>query（必填）——检索查询</li>
 *   <li>topK（可选，默认5）——返回结果数</li>
 *   <li>similarityThreshold（可选，默认0.0）——相似度阈值，低于此值的结果被过滤</li>
 *   <li>output（节点写入检索结果文本）</li>
 * </ul>
 */
@Slf4j
@Component("searchKnowledgeNode")
@RequiredArgsConstructor
public class SearchKnowledgeNode implements JavaDelegate {

    private final HybridSearchService searchService;

    @Override
    public void execute(DelegateExecution execution) {
        var kbId = ((Number) execution.getVariable("knowledgeBaseId")).longValue();
        var query = (String) execution.getVariable("query");
        var topK = execution.getVariable("topK") != null
                ? ((Number) execution.getVariable("topK")).intValue() : 5;
        var similarityThreshold = execution.getVariable("similarityThreshold") != null
                ? ((Number) execution.getVariable("similarityThreshold")).doubleValue() : 0.0;

        var config = new HybridSearchConfig(0.5, 0.3, 0.2, topK);
        var results = searchService.search(query, kbId, config);

        var output = results.stream()
                .filter(r -> r.score() >= similarityThreshold)
                .map(r -> r.content())
                .collect(Collectors.joining("\n\n"));

        execution.setVariable("output", output);
        execution.setVariable("success", true);
    }
}
