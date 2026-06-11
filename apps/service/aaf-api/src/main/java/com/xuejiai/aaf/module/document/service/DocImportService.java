package com.xuejiai.aaf.module.document.service;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.framework.storage.FileVO;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;
import com.xuejiai.aaf.module.system.file.repository.FileRecordRepository;

import lombok.RequiredArgsConstructor;

/** 文档导入服务：从外部源导入文档并提取关联关系。 */
@Service
@RequiredArgsConstructor
public class DocImportService {

    private static final Logger log = LoggerFactory.getLogger(DocImportService.class);

    private final FileService fileService;
    private final FileRecordRepository fileRecordRepository;
    private final DocumentRepository documentRepository;

    /**
     * 导入 PDF：上传原始文件 → 提取文本 → 存入 doc_document。
     *
     * @param file 上传的 PDF 文件
     * @return 创建的文档
     */
    @Transactional
    public Document importPdf(MultipartFile file) throws IOException {
        // 1. 上传原始文件到 OSS，记录 sys_file
        FileVO uploaded = fileService.upload(file);
        FileRecord fileRecord = saveFileRecord(uploaded, file);

        // 2. 提取 PDF 文本
        String text = extractText(file);

        // 3. 存入 doc_document
        String title = stripExtension(file.getOriginalFilename());
        Document doc = new Document();
        doc.setTitle(title);
        doc.setDocType("pdf_import");
        doc.setContent(text);
        doc.setStatus("active");
        doc.setPublish("draft");
        doc.setSourceFileId(fileRecord.getId());
        documentRepository.save(doc);

        log.info("PDF 导入完成：file={}, docId={}", file.getOriginalFilename(), doc.getId());
        return doc;
    }

    /** 提取文档间链接关系（Markdown wikilink 扫描）。 */
    public void extractLinks() {
        // TODO: 实现文档链接提取逻辑
    }

    // ── 私有方法 ──────────────────────────────────────────────

    private FileRecord saveFileRecord(FileVO uploaded, MultipartFile file) {
        FileRecord record = new FileRecord();
        record.setKey(uploaded.key());
        record.setOriginalName(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : uploaded.key());
        record.setMimeType(file.getContentType());
        record.setSize(file.getSize());
        record.setStoragePath(uploaded.url());
        return fileRecordRepository.save(record);
    }

    private String extractText(MultipartFile file) throws IOException {
        try (var doc = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String stripExtension(String filename) {
        if (filename == null) return "未命名文档";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
