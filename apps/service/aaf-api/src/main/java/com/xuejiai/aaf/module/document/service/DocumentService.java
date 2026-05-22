package com.xuejiai.aaf.module.document.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.document.vo.*;

/** 文档管理服务（业务文档基础 CRUD）。 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<DocTreeNodeVO> getTree() {
        return buildTree(documentRepository.findByStatusOrderByFilePath("active"));
    }

    public Document getById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在"));
    }

    @Transactional
    public Document create(DocCreateDTO dto) {
        String filePath = dto.filePath();
        if (filePath != null) {
            Path normalized = Path.of(filePath).normalize();
            if (normalized.toString().replace('\\', '/').contains("..")) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "非法文件路径");
            }
        }
        Document doc = new Document();
        doc.setTitle(dto.title());
        doc.setFilePath(filePath);
        doc.setContent(dto.content() != null ? dto.content() : "");
        doc.setDocType(dto.docType() != null ? dto.docType() : "spec");
        doc.setStatus("active");
        return documentRepository.save(doc);
    }

    @Transactional
    public Document update(Long id, String content) {
        Document doc = getById(id);
        doc.setContent(content);
        documentRepository.save(doc);
        if (doc.getFilePath() != null) writeToLocalFile(doc.getFilePath(), content);
        return doc;
    }

    public List<DocSearchResultVO> search(String query) {
        return documentRepository.fullTextSearch(query).stream()
                .map(d -> new DocSearchResultVO(d.getId(), d.getTitle(), d.getFilePath(),
                        extractSnippet(d.getContent(), query)))
                .collect(Collectors.toList());
    }

    private void writeToLocalFile(String filePath, String content) {
        try {
            Path path = Path.of(filePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            log.error("写回本地文件失败：{}，原因：{}", filePath, e.getMessage());
        }
    }

    private String extractSnippet(String content, String query) {
        if (content == null || query == null) return "";
        int idx = content.toLowerCase().indexOf(query.toLowerCase());
        if (idx < 0) return content.substring(0, Math.min(100, content.length()));
        int start = Math.max(0, idx - 50);
        int end = Math.min(content.length(), idx + query.length() + 50);
        return content.substring(start, end);
    }

    private List<DocTreeNodeVO> buildTree(List<Document> docs) {
        Map<String, DocTreeNodeVO> dirMap = new LinkedHashMap<>();
        List<DocTreeNodeVO> roots = new ArrayList<>();

        for (Document doc : docs) {
            String path = doc.getFilePath() != null ? doc.getFilePath() : doc.getTitle();
            String[] parts = path.split("/");
            DocTreeNodeVO parent = null;
            var currentPath = new StringBuilder();

            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) currentPath.append("/");
                currentPath.append(parts[i]);
                String dirPath = currentPath.toString();
                final int idx = i;
                final DocTreeNodeVO parentRef = parent;
                DocTreeNodeVO dir = dirMap.computeIfAbsent(dirPath, k -> {
                    var node = new DocTreeNodeVO(null, parts[idx], k, true, new ArrayList<>());
                    if (parentRef == null) roots.add(node);
                    else parentRef.children().add(node);
                    return node;
                });
                parent = dir;
            }

            var leaf = new DocTreeNodeVO(doc.getId(), doc.getTitle(), path, false, List.of());
            if (parent != null) parent.children().add(leaf);
            else roots.add(leaf);
        }
        return roots;
    }
}
