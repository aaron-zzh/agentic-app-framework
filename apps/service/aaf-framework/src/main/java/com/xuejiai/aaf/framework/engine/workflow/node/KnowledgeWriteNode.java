package com.xuejiai.aaf.framework.engine.workflow.node;

import java.util.List;
import java.util.Map;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeVectorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库写入节点——工作流中动态写入知识库段落。
 *
 * <p>流程变量：knowledgeBaseId（必填）、content（必填）、output/success（节点写入）
 */
@Slf4j
@Component("knowledgeWriteNode")
@RequiredArgsConstructor
public class KnowledgeWriteNode implements JavaDelegate {

    private final KnowledgeVectorService vectorService;

    @Override
    public void execute(DelegateExecution execution) {
        var kbId = ((Number) execution.getVariable("knowledgeBaseId")).longValue();
        var content = (String) execution.getVariable("content");

        log.info("工作流 KnowledgeWriteNode: kbId={}", kbId);
        var doc = new Document(content, Map.of("knowledgeBaseId", kbId));
        vectorService.store(List.of(doc));
        execution.setVariable("success", true);
        execution.setVariable("output", "已写入知识库");
    }
}
