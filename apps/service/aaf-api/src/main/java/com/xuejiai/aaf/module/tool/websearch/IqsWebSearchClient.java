package com.xuejiai.aaf.module.tool.websearch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云信息查询服务（IQS）联网搜索客户端。
 *
 * <p>接口：{@code POST /search/unified}。仅请求标题、链接、摘要，不请求 mainText/summary（summary 为付费增值项）。 认证与错误解析见
 * {@link IqsHttpClient}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IqsWebSearchClient {

    private static final String SEARCH_PATH = "/search/unified";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final IqsHttpClient http;

    @Value("${aaf.tool.web-search.engine-type:CNLiteBasic}")
    private String engineType;

    /** 单条搜索结果。 */
    public record SearchItem(
            String title, String link, String snippet, String publishedTime, String hostname) {}

    /** 一次搜索的结果与服务端请求 ID。 */
    public record SearchResult(String requestId, List<SearchItem> items) {}

    /**
     * 联网搜索。
     *
     * @param query 查询词（1~1024 字符，由调用方校验）
     * @param count 返回条数
     */
    public SearchResult search(String query, int count) {
        var body =
                Map.of(
                        "query",
                        query,
                        "engineType",
                        engineType,
                        "contents",
                        Map.of(
                                "mainText",
                                false,
                                "markdownText",
                                false,
                                "summary",
                                false,
                                "rerankScore",
                                true),
                        "advancedParams",
                        Map.of("numResults", String.valueOf(count)));
        var root = http.post(SEARCH_PATH, body, REQUEST_TIMEOUT);
        var items = new ArrayList<SearchItem>();
        for (var item : root.path("pageItems")) {
            items.add(
                    new SearchItem(
                            item.path("title").asString(""),
                            item.path("link").asString(""),
                            item.path("snippet").asString(""),
                            item.path("publishedTime").asString(""),
                            item.path("hostname").asString("")));
        }
        var requestId = root.path("requestId").asString("");
        log.info(
                "[WebSearch] 搜索完成: engineType={}, requestId={}, results={}, costCredits={}",
                engineType,
                requestId,
                items.size(),
                root.path("costCredits"));
        return new SearchResult(requestId, List.copyOf(items));
    }
}
