package com.xuejiai.aaf.module.document.service;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;

import lombok.RequiredArgsConstructor;

/** 文档导入服务：从外部源导入文档并提取关联关系。 */
@Service
@RequiredArgsConstructor
public class DocImportService {

    private static final Logger log = LoggerFactory.getLogger(DocImportService.class);

    private final FileStoragePort fileUploadService;
    private final DocumentRepository documentRepository;

    /** 导入 PDF：上传原始文件 → 提取文本 → 存入 doc_document。 */
    @Transactional
    public Document importPdf(MultipartFile file, Long ownerId, Long orgId, Long workspaceId)
            throws IOException {
        var storedFile = fileUploadService.uploadCurrent(file);
        var text = extractText(file);

        var document = new Document();
        document.setTitle(stripExtension(file.getOriginalFilename()));
        document.setDocType("pdf_import");
        document.setContent(text);
        document.setStatus("active");
        document.setPublish("draft");
        document.setSourceFileId(storedFile.fileId());
        document.setOwnerId(ownerId);
        document.setOrgId(orgId);
        document.setWorkspaceId(workspaceId);
        documentRepository.save(document);

        log.info("PDF 导入完成：file={}, docId={}", file.getOriginalFilename(), document.getId());
        return document;
    }

    /** 提取文档间链接关系（Markdown wikilink 扫描）。 */
    public void extractLinks() {
        // TODO: 实现文档链接提取逻辑
    }

    private String extractText(MultipartFile file) throws IOException {
        try (var document = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String stripExtension(String filename) {
        if (filename == null) return "未命名文档";
        var dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
