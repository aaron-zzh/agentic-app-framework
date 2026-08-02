package com.xuejiai.aaf.framework.engine.workflow.node;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.rag.HybridSearchService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.AuthorizedQuery;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.ChannelWeights;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;

import lombok.RequiredArgsConstructor;

/** 工作流知识检索节点；按稳定 UUID 先授权再检索，默认包含公共知识库。 */
@Component("searchKnowledgeNode")
@RequiredArgsConstructor
public class SearchKnowledgeNode implements JavaDelegate {

    private final HybridSearchService searchService;

    @Override
    public void execute(DelegateExecution execution) {
        var query = (String) execution.getVariable("query");
        var topK = integerVariable(execution, "topK", 5);
        var threshold = doubleVariable(execution, "similarityThreshold", 0.0);
        var knowledgeBaseIds = parseKnowledgeBaseIds(execution.getVariable("knowledgeBaseIds"));
        var authorizedQuery =
                new AuthorizedQuery(
                        AuthorizationSubject.unresolved(),
                        query,
                        knowledgeBaseIds,
                        true,
                        Map.of(),
                        ChannelWeights.defaults(),
                        topK,
                        threshold,
                        Map.of());
        var output =
                searchService.search(authorizedQuery).hits().stream()
                        .map(hit -> hit.content())
                        .collect(Collectors.joining("\n\n"));

        execution.setVariable("output", output);
        execution.setVariable("success", true);
    }

    private Set<UUID> parseKnowledgeBaseIds(Object value) {
        return switch (value) {
            case null -> Set.of();
            case Collection<?> values ->
                    values.stream()
                            .map(Object::toString)
                            .map(UUID::fromString)
                            .collect(Collectors.toUnmodifiableSet());
            default ->
                    java.util.Arrays.stream(value.toString().split(","))
                            .map(String::trim)
                            .filter(item -> !item.isEmpty())
                            .map(UUID::fromString)
                            .collect(Collectors.toUnmodifiableSet());
        };
    }

    private int integerVariable(DelegateExecution execution, String name, int defaultValue) {
        var value = execution.getVariable(name);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private double doubleVariable(DelegateExecution execution, String name, double defaultValue) {
        var value = execution.getVariable(name);
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }
}
