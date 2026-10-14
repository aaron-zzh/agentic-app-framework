package com.xuejiai.aaf.framework.intelligent.ai.rerank;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 DashScope HTTP API 的重排序实现，用于 RAG 精排。
 *
 * <p>支持模型：qwen3.7-text-rerank、qwen3-rerank 等。
 *
 * <p>启用条件：配置 {@code spring.ai.dashscope.api-key}
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeRerankService implements RerankService {

    /** 重排序接口路径；域名取自模型 base_url（工作区域名，如 https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com）。 */
    private static final String RERANK_PATH = "/api/v1/services/rerank/text-rerank/text-rerank";

    private final String fallbackApiKey;
    private final CapabilityRouter capabilityRouter;
    private final AiModelRepository modelRepository;
    private final AiCreditGuard creditGuard;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DashScopeRerankService(
            @Value("${spring.ai.dashscope.api-key:}") String fallbackApiKey,
            CapabilityRouter capabilityRouter,
            AiModelRepository modelRepository,
            AiCreditGuard creditGuard) {
        this.fallbackApiKey = fallbackApiKey;
        this.capabilityRouter = capabilityRouter;
        this.modelRepository = modelRepository;
        this.creditGuard = creditGuard;
    }

    @Override
    public List<RankedDocument> rerank(
            Long userId, String query, List<String> documents, int topN) {
        Objects.requireNonNull(userId, "重排序成本需要归属用户，userId 不能为空");
        try {
            // 经六级链按 RERANK 能力解析 modelId（系统默认 / yaml / 内置兜底 qwen3.7-text-rerank）
            var aiModel =
                    capabilityRouter.resolve(
                            CapabilityRoutingContext.ofCapability(
                                    null, CapabilityRoutingContext.CAP_RERANK));
            creditGuard.precheck(
                    userId, CapabilityRoutingContext.CAP_RERANK, AiCreditGuard.INESTIMABLE_COST);
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
            var baseUrl = aiModel.effectiveBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("重排序模型缺少 base_url: " + aiModel.getModelId());
            }
            var url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            var request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url + RERANK_PATH))
                            .header("Authorization", "Bearer " + key)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var json = JsonUtils.readTree(response.body());
            var promptTokens = json.path("usage").path("prompt_tokens").asLong(0);
            if (promptTokens <= 0) {
                throw new IllegalStateException("重排序响应缺少 usage.prompt_tokens，无法按量计费");
            }
            log.info(
                    "[Rerank] 重排序完成: userId={}, modelId={}, requestId={}, documents={}, topN={},"
                            + " promptTokens={}",
                    userId,
                    aiModel.getModelId(),
                    json.path("request_id").asString(""),
                    documents.size(),
                    topN,
                    promptTokens);
            creditGuard.settleByUsage(
                    userId,
                    aiModel,
                    new RerankUsage(promptTokens, documents.size()),
                    CapabilityRoutingContext.CAP_RERANK,
                    "重排序");
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

    private record RerankUsage(long inputTokens, int count) implements AiUsage {
        @Override
        public Map<String, Object> standardUsage() {
            return Map.of("inputTokens", inputTokens, "outputTokens", 0, "count", count);
        }
    }
}
