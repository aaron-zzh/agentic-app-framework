package com.xuejiai.aaf.framework.engine.knowledge.importer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 文档导入解析期资源限制配置——AAF-114 #11410 加固各 {@link DocumentImporter} 的解析边界。 */
@ConfigurationProperties(prefix = "aaf.knowledge.document-import")
public record DocumentImportLimits(
        int maxPdfPages, long parseTimeoutMillis, long maxCharacters, int maxZipEntries) {

    public DocumentImportLimits {
        if (maxPdfPages <= 0) maxPdfPages = 200;
        if (parseTimeoutMillis <= 0) parseTimeoutMillis = 30_000;
        if (maxCharacters <= 0) maxCharacters = 200_000;
        if (maxZipEntries <= 0) maxZipEntries = 1000;
    }

    public DocumentImportLimits() {
        this(0, 0, 0, 0);
    }
}
