package com.xuejiai.aaf.module.ai.assistant.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.importer.DocumentSection;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImportResult;

import lombok.RequiredArgsConstructor;

/**
 * 文档内容 Token 预算裁剪器——把 {@link ImportResult} 的段落列表按预算裁剪为可审计的最终文本。
 *
 * <p>裁剪策略：按段落顺序累加拼接，超预算时停止累加并显式标记截断（不静默丢弃、不生成摘要伪装成完整内容）。单段超过 {@link
 * DocumentTokenBudgetLimits#maxCharsPerSection()} 时先裁剪该段。
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(DocumentTokenBudgetSplitter.DocumentTokenBudgetLimits.class)
public class DocumentTokenBudgetSplitter {

    private final DocumentTokenBudgetLimits limits;

    /**
     * 按预算裁剪文档内容。
     *
     * @param result importer 解析结果
     * @param resourceId opaque 文件 key，用于 provenance 记录
     * @param contentHash 文件内容 SHA-256，用于 provenance 记录
     * @param importerName importer 类名，用于 provenance 记录
     * @return 裁剪后的文本与 provenance
     */
    public SplitResult split(
            ImportResult result, String resourceId, String contentHash, String importerName) {
        var sections = result.sections();
        var builder = new StringBuilder();
        var truncated = false;

        for (DocumentSection section : sections) {
            var text = clipSection(section.content());
            var addition = builder.isEmpty() ? text : "\n\n" + text;
            if (builder.length() + addition.length() > limits.maxCharsPerAttachment()) {
                truncated = true;
                break;
            }
            builder.append(addition);
        }

        var originalCharacters = (int) result.totalCharacters();
        var deliveredCharacters = builder.length();
        var provenance =
                new Provenance(
                        resourceId,
                        contentHash,
                        importerName,
                        DocumentImporterVersion.CURRENT,
                        truncated,
                        originalCharacters,
                        deliveredCharacters);
        return new SplitResult(builder.toString(), truncated, provenance);
    }

    private String clipSection(String content) {
        if (content.length() <= limits.maxCharsPerSection()) {
            return content;
        }
        return content.substring(0, limits.maxCharsPerSection());
    }

    /** 裁剪结果。 */
    public record SplitResult(String content, boolean truncated, Provenance provenance) {}

    /** 输出 provenance——记录 opaque resource ID、content hash、importer 名称/版本与截断状态。 */
    public record Provenance(
            String resourceId,
            String contentHash,
            String importerName,
            String importerVersion,
            boolean truncated,
            int originalCharacters,
            int deliveredCharacters) {

        /** 供审计日志使用的单行摘要，不拼入模型可见文本。 */
        public String toLogSummary() {
            return "resourceId=%s contentHash=%s importer=%s@%s truncated=%s original=%d delivered=%d"
                    .formatted(
                            resourceId,
                            contentHash,
                            importerName,
                            importerVersion,
                            truncated,
                            originalCharacters,
                            deliveredCharacters);
        }
    }

    /** Token 预算裁剪配置。 */
    @ConfigurationProperties(prefix = "aaf.assistant.document-token-budget")
    public record DocumentTokenBudgetLimits(int maxCharsPerAttachment, int maxCharsPerSection) {

        public DocumentTokenBudgetLimits {
            if (maxCharsPerAttachment <= 0) maxCharsPerAttachment = 50_000;
            if (maxCharsPerSection <= 0) maxCharsPerSection = 5_000;
        }

        public DocumentTokenBudgetLimits() {
            this(0, 0);
        }
    }

    /** importer 版本标识——当前 importer 未做独立版本管理，固定为框架版本占位值。 */
    private static final class DocumentImporterVersion {
        static final String CURRENT = "1.0.0";

        private DocumentImporterVersion() {}
    }
}
