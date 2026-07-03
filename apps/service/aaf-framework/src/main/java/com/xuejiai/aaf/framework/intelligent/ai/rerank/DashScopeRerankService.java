package com.xuejiai.aaf.framework.intelligent.ai.rerank;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 DashScope HTTP API 的重排序实现，用于 RAG 精排。
 *
 * <p>支持模型：qwen3-rerank、qwen3-rerank 等。
 *
 * <p>启用条件：配置 {@code spring.ai.dashscope.api-key}
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeRerankService implements RerankService {

    private static final String RERANK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final String fallbackApiKey;
    private final CapabilityRouter capabilityRouter;
    private final AiModelRepository modelRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DashScopeRerankService(
            @Value("${spring.ai.dashscope.api-key:}") String fallbackApiKey,
            CapabilityRouter capabilityRouter,
            AiModelRepository modelRepository) {
        this.fallbackApiKey = fallbackApiKey;
        this.capabilityRouter = capabilityRouter;
        this.modelRepository = modelRepository;
    }

    @Override
    public List<RankedDocument> rerank(String query, List<String> documents, int topN) {
        try {
            // 经六级链按 RERANK 能力解析 modelId（系统默认 / yaml / 内置兜底 qwen3-rerank）
            var aiModel =
                    capabilityRouter.resolve(
                            CapabilityRoutingContext.ofCapability(
                                    null, CapabilityRoutingContext.CAP_RERANK));
            var modelName = aiModel.getModelName();
            var key =
                    aiModel.effectiveApiKey() != null ? aiModel.effectiveApiKey() : fallbackApiKey;
            var body =
                    JsonUtils.toJsonString(
                            java.util.Map.of(
                                    "model", modelName,
                                    "input",
                                            java.util.Map.of(
                                                    "query", query,
                                                    "documents", documents),
                                    "parameters",
                                            java.util.Map.of(
                                                    "top_n", topN, "return_documents", true)));
            var request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(RERANK_URL))
                            .header("Authorization", "Bearer " + key)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var json = JsonUtils.readTree(response.body());
            var results = json.path("output").path("results");
            return java.util.stream.StreamSupport.stream(results.spliterator(), false)
                    .map(
                            r ->
                                    new RankedDocument(
                                            r.path("index").asInt(),
                                            r.path("document").path("text").asString(""),
                                            r.path("relevance_score").asDouble()))
                    .toList();
        } catch (IOException | InterruptedException e) {
            log.error("[DashScopeRerank] 重排序失败: query={}", query, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("重排序失败: " + e.getMessage(), e);
        }
    }
}
