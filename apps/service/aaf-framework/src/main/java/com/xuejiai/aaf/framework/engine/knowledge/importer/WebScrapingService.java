package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 网页抓取服务，支持单页/批量抓取和 sitemap 解析 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(WebScrapingProperties.class)
public class WebScrapingService {

    private static final Set<String> NOISE_TAGS =
            Set.of("script", "style", "nav", "footer", "header", "aside");
    private static final Set<String> HEADING_TAGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");

    private final WebScrapingProperties properties;

    /** 单页抓取 + 正文提取 */
    public ImportResult scrapeUrl(String url) {
        var doc = fetchWithRetry(url);
        var title = doc.title().isBlank() ? url : doc.title();
        var body = doc.body();
        if (body == null) {
            return new ImportResult(List.of(), title, 0);
        }

        // 移除噪声标签
        NOISE_TAGS.forEach(tag -> body.select(tag).remove());

        // 找文本密度最高的元素作为正文容器
        var contentRoot = findContentRoot(body);

        // 解析为 sections
        var sections = extractSections(contentRoot);
        long totalChars = sections.stream().mapToLong(s -> s.content().length()).sum();
        return new ImportResult(sections, title, totalChars);
    }

    /** 批量抓取，每个 URL 间隔速率限制 */
    public List<ImportResult> scrapeBatch(List<String> urls) {
        var results = new ArrayList<ImportResult>();
        for (int i = 0; i < urls.size(); i++) {
            try {
                results.add(scrapeUrl(urls.get(i)));
            } catch (Exception e) {
                log.warn("抓取失败: {}", urls.get(i), e);
            }
            // 速率限制（最后一个不等待）
            if (i < urls.size() - 1) {
                sleep(properties.delayBetweenRequests());
            }
        }
        return results;
    }

    /** 解析 sitemap.xml，提取所有 URL */
    public List<String> parseSitemap(String sitemapUrl) {
        var doc = fetchWithRetry(sitemapUrl);
        // 用 XML 解析器重新解析
        var xmlDoc = Jsoup.parse(doc.html(), "", Parser.xmlParser());
        return xmlDoc.select("loc").stream().map(Element::text).filter(s -> !s.isBlank()).toList();
    }

    /** 找文本密度最高的元素（文本长度 / max(子元素数, 1)） */
    private Element findContentRoot(Element body) {
        return body.select("div, article, section, main").stream()
                .filter(el -> !el.text().isBlank())
                .max(Comparator.comparingDouble(this::textDensity))
                .orElse(body);
    }

    private double textDensity(Element el) {
        int childCount = Math.max(el.children().size(), 1);
        return (double) el.text().length() / childCount;
    }

    /** 将元素内容解析为 DocumentSection 列表 */
    private List<DocumentSection> extractSections(Element root) {
        var sections = new ArrayList<DocumentSection>();
        var currentText = new StringBuilder();
        int currentLevel = 0;

        for (var child : root.children()) {
            var tag = child.tagName().toLowerCase();
            if (HEADING_TAGS.contains(tag)) {
                if (!currentText.isEmpty()) {
                    sections.add(
                            new DocumentSection(
                                    currentText.toString().strip(), currentLevel, Map.of()));
                    currentText.setLength(0);
                }
                currentLevel = Integer.parseInt(tag.substring(1));
                currentText.append(child.text());
            } else {
                var text = child.text();
                if (!text.isBlank()) {
                    if (!currentText.isEmpty()) currentText.append("\n");
                    currentText.append(text);
                }
            }
        }
        if (!currentText.isEmpty()) {
            sections.add(
                    new DocumentSection(currentText.toString().strip(), currentLevel, Map.of()));
        }
        return sections;
    }

    /** 带重试的页面获取 */
    private Document fetchWithRetry(String url) {
        IOException lastException = null;
        for (int i = 0; i <= properties.maxRetries(); i++) {
            try {
                return Jsoup.connect(url)
                        .userAgent(properties.userAgent())
                        .timeout(properties.connectTimeout())
                        .maxBodySize(0)
                        .get();
            } catch (IOException e) {
                lastException = e;
                log.debug("抓取重试 {}/{}: {}", i + 1, properties.maxRetries(), url);
                if (i < properties.maxRetries()) sleep(properties.delayBetweenRequests());
            }
        }
        throw new RuntimeException("抓取失败: " + url, lastException);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
