package com.xuejiai.aaf.module.ai.assistant.document;

import static com.xuejiai.aaf.module.ai.assistant.AssistantErrorCode.EXECUTION_DOCUMENT_ATTACHMENT_PARSE_FAILED;
import static com.xuejiai.aaf.module.ai.assistant.AssistantErrorCode.EXECUTION_DOCUMENT_ATTACHMENT_SCAN_REJECTED;
import static com.xuejiai.aaf.module.ai.assistant.AssistantErrorCode.EXECUTION_DOCUMENT_ATTACHMENT_TOO_LARGE;
import static com.xuejiai.aaf.module.ai.assistant.AssistantErrorCode.EXECUTION_DOCUMENT_ATTACHMENT_TYPE_MISMATCH;
import static com.xuejiai.aaf.module.ai.assistant.AssistantErrorCode.EXECUTION_DOCUMENT_ATTACHMENT_TYPE_UNSUPPORTED;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.knowledge.importer.DocumentImporter;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImportResult;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;
import com.xuejiai.aaf.framework.engine.knowledge.security.DocumentMagicBytes;
import com.xuejiai.aaf.framework.engine.knowledge.security.FileSecurityScanPort;
import com.xuejiai.aaf.framework.engine.knowledge.security.FileSecurityScanPort.ScanStatus;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档附件解析前门禁——唯一入口，在调用 {@link ImporterFactory} 之前完成 owner、扩展名/MIME/魔数一致性、大小与安全扫描校验。
 *
 * <p>校验顺序：owner（复用 {@link FileStoragePort#requireCurrentOwnerByKey}，与 {@code VisionMediaResolver}
 * 同模式，同时隐含拒绝 {@code DELETED} 状态）→ 扩展名/MIME 白名单交叉校验 → 大小 → 魔数一致性 → 安全扫描 → {@link ImporterFactory}
 * 解析。只有全部通过才把解析结果转换为可进入执行链路的清理文本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(DocumentAttachmentGuardService.DocumentAttachmentLimits.class)
public class DocumentAttachmentGuardService {

    /** 扩展名 → 期望 MIME 类型白名单（AAF 支持的五类文档格式）。 */
    private static final Map<String, String> EXTENSION_MIME_TYPES =
            Map.of(
                    "pdf", "application/pdf",
                    "docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "md", "text/markdown",
                    "markdown", "text/markdown",
                    "html", "text/html",
                    "htm", "text/html",
                    "txt", "text/plain");

    private final FileStoragePort fileStoragePort;
    private final FileSecurityScanPort fileSecurityScanPort;
    private final ImporterFactory importerFactory;
    private final DocumentAttachmentLimits limits;

    /**
     * 校验并解析文档附件，返回清理后的段落结果与 provenance 所需元信息。
     *
     * @param fileKey 当前用户拥有的文档文件 key
     * @return importer 解析结果与 provenance 元信息
     * @throws BusinessException owner 校验、类型一致性、大小、安全扫描或解析失败时抛出
     */
    public GuardedDocument guardAndParse(String fileKey) {
        var storedFile = fileStoragePort.requireCurrentOwnerByKey(fileKey);
        var extension = extractExtension(storedFile.originalName());
        var expectedMime = EXTENSION_MIME_TYPES.get(extension);
        if (expectedMime == null) {
            throw new BusinessException(
                    EXECUTION_DOCUMENT_ATTACHMENT_TYPE_UNSUPPORTED, "不支持的文档扩展名: " + extension);
        }
        if (storedFile.mimeType() != null && !expectedMime.equals(storedFile.mimeType())) {
            throw new BusinessException(
                    EXECUTION_DOCUMENT_ATTACHMENT_TYPE_MISMATCH,
                    "文档 MIME 与扩展名不一致: 期望 %s 实际 %s".formatted(expectedMime, storedFile.mimeType()));
        }
        if (storedFile.size() > limits.maxSizeBytes()) {
            throw new BusinessException(
                    EXECUTION_DOCUMENT_ATTACHMENT_TOO_LARGE,
                    "文档大小 %d 超过限制 %d".formatted(storedFile.size(), limits.maxSizeBytes()));
        }

        byte[] content;
        try (var input = fileStoragePort.openByKey(fileKey)) {
            content = input.readAllBytes();
        } catch (IOException failure) {
            log.warn("文档附件读取失败: fileKey={}", fileKey, failure);
            throw new BusinessException(
                    EXECUTION_DOCUMENT_ATTACHMENT_PARSE_FAILED, "文档读取失败: " + fileKey);
        }

        if (!DocumentMagicBytes.matches(content, extension)) {
            throw new BusinessException(
                    EXECUTION_DOCUMENT_ATTACHMENT_TYPE_MISMATCH,
                    "文档扩展名与实际内容不一致: " + storedFile.originalName());
        }

        var scanResult =
                fileSecurityScanPort.scan(
                        content, storedFile.originalName(), storedFile.mimeType());
        if (scanResult.status() != ScanStatus.CLEAN) {
            log.warn(
                    "文档附件未通过安全扫描: fileKey={}, status={}, scanner={}, detail={}",
                    fileKey,
                    scanResult.status(),
                    scanResult.scannerName(),
                    scanResult.detail());
            throw new BusinessException(
                    EXECUTION_DOCUMENT_ATTACHMENT_SCAN_REJECTED,
                    "文档未通过安全扫描: " + storedFile.originalName());
        }

        var importer = importerFactory.getImporter(storedFile.originalName());
        if (importer.isEmpty()) {
            throw new BusinessException(
                    EXECUTION_DOCUMENT_ATTACHMENT_TYPE_UNSUPPORTED, "没有可用的文档导入器: " + extension);
        }
        var result = parse(importer.get(), content, storedFile.originalName());
        return new GuardedDocument(
                result,
                fileKey,
                storedFile.contentHash(),
                importer.get().getClass().getSimpleName());
    }

    private ImportResult parse(DocumentImporter importer, byte[] content, String filename) {
        try (var input = new ByteArrayInputStream(content)) {
            return importer.importDocument(input, filename);
        } catch (IOException failure) {
            log.warn("文档附件解析失败: filename={}", filename, failure);
            throw new BusinessException(
                    EXECUTION_DOCUMENT_ATTACHMENT_PARSE_FAILED, "文档解析失败: " + filename);
        }
    }

    private String extractExtension(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /** 文档附件大小与数量限制配置。 */
    @ConfigurationProperties(prefix = "aaf.assistant.document-attachment")
    public record DocumentAttachmentLimits(long maxSizeBytes, int maxCountPerMessage) {

        public DocumentAttachmentLimits {
            if (maxSizeBytes <= 0) maxSizeBytes = 20L * 1024 * 1024;
            if (maxCountPerMessage <= 0) maxCountPerMessage = 5;
        }

        public DocumentAttachmentLimits() {
            this(0, 0);
        }
    }

    /** 支持的文档扩展名集合，供 controller 分流判断是否走文档处理链路。 */
    public static Set<String> supportedExtensions() {
        return EXTENSION_MIME_TYPES.keySet();
    }

    /** 单次请求允许的文档附件数量上限，供 controller 分流时校验。 */
    public int maxCountPerMessage() {
        return limits.maxCountPerMessage();
    }

    /** 门禁通过后的解析结果，携带 {@link DocumentTokenBudgetSplitter} 所需的 provenance 元信息。 */
    public record GuardedDocument(
            ImportResult result, String resourceId, String contentHash, String importerName) {}
}
