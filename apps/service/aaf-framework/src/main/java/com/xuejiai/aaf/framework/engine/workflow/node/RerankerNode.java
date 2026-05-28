package com.xuejiai.aaf.framework.engine.workflow.node;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.ai.rerank.RerankService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 重排序节点——对检索结果进行 LLM 重排序。
 *
 * <p>流程变量：query、documents（换行分隔文本）、topK（可选）、output（重排后文本）
 */
@Slf4j
@Component("rerankerNode")
@RequiredArgsConstructor
public class RerankerNode implements JavaDelegate {

    private final RerankService rerankService;

    @Override
    public void execute(DelegateExecution execution) {
        var query = (String) execution.getVariable("query");
        var documentsText = (String) execution.getVariable("documents");
        var topK = execution.getVariable("topK") != null
                ? ((Number) execution.getVariable("topK")).intValue() : 3;

        var documents = Arrays.asList(documentsText.split("\n\n"));
        var reranked = rerankService.rerank(query, documents, topK);

        var output = reranked.stream()
                .map(r -> r.text())
                .collect(Collectors.joining("\n\n"));

        execution.setVariable("output", output);
        execution.setVariable("success", true);
    }
}
