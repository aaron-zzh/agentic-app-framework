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

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 DashScope HTTP API 的重排序实现，用于 RAG 精排。
 *
 * <p>支持模型：gte-rerank-v2、qwen3-rerank 等。
 *
 * <p>启用条件：配置 {@code spring.ai.dashscope.api-key}
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeRerankService implements RerankService {

    private static final String RERANK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
    private static final String DEFAULT_MODEL = "gte-rerank-v2";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeRerankService(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public List<RankedDocument> rerank(String query, List<String> documents, int topN) {
        try {
            var body =
                    objectMapper.writeValueAsString(
                            java.util.Map.of(
                                    "model", DEFAULT_MODEL,
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
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var json = objectMapper.readTree(response.body());
            var results = json.path("output").path("results");
            return java.util.stream.StreamSupport.stream(results.spliterator(), false)
                    .map(
                            r ->
                                    new RankedDocument(
                                            r.path("index").asInt(),
                                            r.path("document").path("text").asText(""),
                                            r.path("relevance_score").asDouble()))
                    .toList();
        } catch (IOException | InterruptedException e) {
            log.error("[DashScopeRerank] 重排序失败: query={}", query, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("重排序失败: " + e.getMessage(), e);
        }
    }
}
