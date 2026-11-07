package com.xuejiai.aaf.module.document.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.knowledge.importer.DocumentSection;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.CreateDocumentVersionCommand;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;

import lombok.RequiredArgsConstructor;

/**
 * 文档导入服务：保存上传原文件并固化不可变初始版本。
 *
 * <p>文本类（md/markdown/txt）正文即原文，可继续编辑；PDF 只能提取部分文本，正文为只读提取结果，原文件以 {@code sourceFileId} 保留供预览。
 */
@Service
@RequiredArgsConstructor
public class DocImportService {

    /** PDF 提取文本文档类型；该类型文档只读，见 {@link DocumentService#update}。 */
    public static final String PDF_IMPORT_DOC_TYPE = "pdf_import";

    private static final String TEXT_IMPORT_DOC_TYPE = "reference";
    private static final Set<String> MARKDOWN_EXTENSIONS = Set.of("md", "markdown");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("md", "markdown", "txt", "pdf");

    private static final Logger log = LoggerFactory.getLogger(DocImportService.class);

    private final FileStoragePort fileUploadService;
    private final DocumentRepository documentRepository;
    private final DocumentVersionService documentVersionService;
    private final ImporterFactory importerFactory;

    /** 是否支持导入为文档库文档。 */
    public static boolean supports(String filename) {
        return SUPPORTED_EXTENSIONS.contains(extension(filename));
    }

    /** 导入文件：上传原始文件 → 取得正文 → 创建文档身份与 IMPORT v1；事务回滚时清理已上传原文件。 */
    @Transactional
    public Document importFile(MultipartFile file, Long ownerId, Long orgId, Long workspaceId)
            throws IOException {
        var filename = file.getOriginalFilename();
        var ext = extension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不支持导入该文件类型: " + filename);
        }
        var isPdf = "pdf".equals(ext);
        var content = isPdf ? extractPdfText(file) : decodeUtf8(file);
        if (content.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文件没有可提取的文本: " + filename);
        }

        var storedFile = fileUploadService.uploadCurrent(file);
        deleteOnRollback(storedFile.fileId());
        var version =
                documentVersionService.create(
                        new CreateDocumentVersionCommand(
                                stripExtension(filename),
                                content,
                                MARKDOWN_EXTENSIONS.contains(ext) ? "MARKDOWN" : "PLAIN_TEXT",
                                isPdf ? PDF_IMPORT_DOC_TYPE : TEXT_IMPORT_DOC_TYPE,
                                "IMPORT",
                                storedFile.key(),
                                isPdf ? "导入 PDF" : "导入文本文件",
                                ownerId,
                                orgId,
                                workspaceId));
        var document = documentRepository.findById(version.documentId()).orElseThrow();
        document.setSourceFileId(storedFile.fileId());
        documentRepository.save(document);
        documentVersionService.hydrateCurrent(document);

        log.info("文件导入完成：file={}, docId={}", filename, document.getId());
        return document;
    }

    /** 提取文档间链接关系（Markdown wikilink 扫描）。 */
    public void extractLinks() {
        // TODO: 实现文档链接提取逻辑
    }

    /** 复用知识库 PDF 导入器（含页数、字符与超时限制），按页拼接提取文本；解析失败按请求错误返回。 */
    private String extractPdfText(MultipartFile file) {
        var importer =
                importerFactory
                        .getImporter(file.getOriginalFilename())
                        .orElseThrow(() -> new IllegalStateException("PDF 导入器未注册"));
        try (var input = file.getInputStream()) {
            return importer.importDocument(input, file.getOriginalFilename()).sections().stream()
                    .map(DocumentSection::content)
                    .collect(Collectors.joining("\n\n"));
        } catch (IOException failure) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, failure.getMessage());
        }
    }

    private String decodeUtf8(MultipartFile file) throws IOException {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文本文件必须为 UTF-8 编码");
        }
    }

    private void deleteOnRollback(Long fileId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_ROLLED_BACK) {
                            return;
                        }
                        try {
                            fileUploadService.requestDelete(fileId);
                        } catch (RuntimeException failure) {
                            log.warn("回滚后清理导入原文件失败，fileId={}", fileId, failure);
                        }
                    }
                });
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        var dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String filename) {
        if (filename == null) return "未命名文档";
        var dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
