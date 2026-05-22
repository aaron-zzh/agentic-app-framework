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
import com.xuejiai.aaf.module.document.domain.DocLink;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocLinkRepository;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.module.document.vo.*;

/** 文档管理服务。 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocLinkRepository docLinkRepository;
    private final DocImportService docImportService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocLinkRepository docLinkRepository,
            DocImportService docImportService) {
        this.documentRepository = documentRepository;
        this.docLinkRepository = docLinkRepository;
        this.docImportService = docImportService;
    }

    /** 获取文档树（按 file_path 构建层级结构）。 */
    public List<DocTreeNodeVO> getTree() {
        List<Document> docs = documentRepository.findByStatusOrderByFilePath("active");
        return buildTree(docs);
    }

    /** 获取文档详情。 */
    public Document getById(Long id) {
        return documentRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在"));
    }

    /** 更新文档内容，同步写回本地文件。 */
    @Transactional
    public Document update(Long id, String content) {
        Document doc = getById(id);
        doc.setContent(content);
        documentRepository.save(doc);

        if (doc.getFilePath() != null) {
            writeToLocalFile(doc.getFilePath(), content);
        }

        docImportService.extractLinks();
        return doc;
    }

    /** 全文检索。 */
    public List<DocSearchResultVO> search(String query) {
        return documentRepository.fullTextSearch(query).stream()
                .map(
                        doc ->
                                new DocSearchResultVO(
                                        doc.getId(),
                                        doc.getTitle(),
                                        doc.getFilePath(),
                                        extractSnippet(doc.getContent(), query)))
                .collect(Collectors.toList());
    }

    /** 获取文档关系图数据（nodes + edges，1 跳）。 */
    public DocRelationGraphVO getRelations(Long id) {
        getById(id); // 校验存在

        List<DocLink> outgoing = docLinkRepository.findBySourceId(id);
        List<DocLink> incoming = docLinkRepository.findByTargetId(id);

        Set<Long> nodeIds = new HashSet<>();
        nodeIds.add(id);
        outgoing.forEach(l -> nodeIds.add(l.getTargetId()));
        incoming.forEach(l -> nodeIds.add(l.getSourceId()));

        List<Document> nodes = documentRepository.findAllById(nodeIds);

        List<DocRelationGraphVO.Edge> edges = new ArrayList<>();
        outgoing.forEach(
                l ->
                        edges.add(
                                new DocRelationGraphVO.Edge(
                                        l.getSourceId(), l.getTargetId(), l.getLinkType())));
        incoming.forEach(
                l ->
                        edges.add(
                                new DocRelationGraphVO.Edge(
                                        l.getSourceId(), l.getTargetId(), l.getLinkType())));

        List<DocRelationGraphVO.Node> graphNodes =
                nodes.stream()
                        .map(
                                d ->
                                        new DocRelationGraphVO.Node(
                                                d.getId(),
                                                d.getTitle(),
                                                d.getFilePath(),
                                                d.getId().equals(id)))
                        .collect(Collectors.toList());

        return new DocRelationGraphVO(graphNodes, edges);
    }

    private void writeToLocalFile(String filePath, String content) {
        try {
            Path path = Path.of(filePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            log.info("文档已同步写回本地：{}", filePath);
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
                DocTreeNodeVO dir =
                        dirMap.computeIfAbsent(
                                dirPath,
                                k -> {
                                    var node =
                                            new DocTreeNodeVO(
                                                    null, parts[idx], k, true, new ArrayList<>());
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
