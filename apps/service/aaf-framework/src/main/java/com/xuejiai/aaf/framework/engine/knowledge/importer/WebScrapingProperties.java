package com.xuejiai.aaf.framework.engine.knowledge.importer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 网页抓取配置 */
@ConfigurationProperties(prefix = "aaf.knowledge.scraping")
public record WebScrapingProperties(
        String userAgent,
        int connectTimeout,
        int readTimeout,
        int delayBetweenRequests,
        int maxRetries) {
    public WebScrapingProperties {
        if (userAgent == null || userAgent.isBlank()) {
            userAgent =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
        }
        if (connectTimeout <= 0) connectTimeout = 10000;
        if (readTimeout <= 0) readTimeout = 30000;
        if (delayBetweenRequests <= 0) delayBetweenRequests = 1000;
        if (maxRetries <= 0) maxRetries = 2;
    }

    public WebScrapingProperties() {
        this(null, 0, 0, 0, 0);
    }
}
