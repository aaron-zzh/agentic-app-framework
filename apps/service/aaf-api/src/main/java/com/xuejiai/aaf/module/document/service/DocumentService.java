package com.xuejiai.aaf.module.document.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    /** SSE 订阅者（docId=0 表示全局订阅） */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> subscribers =
            new ConcurrentHashMap<>();

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

    /** 更新文档（标题/内容/类型/发布状态），同步写回本地文件。 */
    @Transactional
    public Document update(Long id, DocUpdateDTO dto) {
        Document doc = getById(id);
        if (dto.title() != null) doc.setTitle(dto.title());
        if (dto.content() != null) doc.setContent(dto.content());
        if (dto.docType() != null) doc.setDocType(dto.docType());
        if (dto.publish() != null) doc.setPublish(dto.publish());
        documentRepository.save(doc);

        if (dto.content() != null && doc.getFilePath() != null) {
            writeToLocalFile(doc.getFilePath(), doc.getContent());
        }

        docImportService.extractLinks();
        broadcastChange(id, doc.getTitle());
        return doc;
    }

    /** 发布文档。 */
    @Transactional
    public Document publish(Long id) {
        Document doc = getById(id);
        doc.setPublish("published");
        documentRepository.save(doc);
        broadcastChange(id, doc.getTitle());
        return doc;
    }

    /** 取消发布（转草稿）。 */
    @Transactional
    public Document unpublish(Long id) {
        Document doc = getById(id);
        doc.setPublish("draft");
        documentRepository.save(doc);
        broadcastChange(id, doc.getTitle());
        return doc;
    }

    /** 获取所有已发布文档（公开端）。 */
    public List<Document> getPublished() {
        return documentRepository.findByPublishOrderByUpdateTimeDesc("published");
    }

    /** 新建文档：写入本地文件 + 插入数据库 + 提取链接。 */
    @Transactional
    public DocTreeNodeVO create(DocCreateDTO dto) {
        String content = dto.content() != null ? dto.content() : "";

        // filePath 有值时才做路径安全校验和本地文件写入
        if (dto.filePath() != null && !dto.filePath().isBlank()) {
            validateFilePath(dto.filePath());
            writeToLocalFile(dto.filePath(), content);
        }

        // 插入数据库
        var doc = new Document();
        doc.setTitle(dto.title());
        doc.setFilePath(dto.filePath());
        doc.setDocType(dto.docType() != null ? dto.docType() : "guide");
        doc.setContent(content);
        doc.setStatus("active");
        doc.setPublish(dto.publish() != null ? dto.publish() : "draft");
        documentRepository.save(doc);

        // 提取链接关系
        docImportService.extractLinks();

        return new DocTreeNodeVO(doc.getId(), doc.getTitle(), doc.getFilePath(), false, List.of());
    }

    /** 订阅文档变更事件（SSE）。 */
    public SseEmitter subscribe(Long docId) {
        var emitter = new SseEmitter(5 * 60 * 1000L);
        var list = subscribers.computeIfAbsent(docId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    /** 导入 PDF（委托 DocImportService）。 */
    public Document importPdf(MultipartFile file) throws IOException {
        return docImportService.importPdf(file);
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

    private void validateFilePath(String filePath) {
        if (filePath == null || !filePath.startsWith("docs/")) {
            throw new IllegalArgumentException("filePath 必须以 docs/ 开头");
        }
        // 规范化后检查是否仍在 docs/ 目录下（防 ../穿越）
        Path normalized = Path.of(filePath).normalize();
        if (!normalized.startsWith("docs")) {
            throw new IllegalArgumentException("filePath 包含非法路径穿越");
        }
        // 禁止包含空字节（防 null-byte 注入）
        if (filePath.contains("\0")) {
            throw new IllegalArgumentException("filePath 包含非法字符");
        }
    }

    private void broadcastChange(Long docId, String title) {
        String json =
                "{\"type\":\"doc_updated\",\"docId\":%d,\"title\":\"%s\"}"
                        .formatted(docId, title != null ? title.replace("\"", "\\\"") : "");
        // 通知特定文档订阅者
        sendToSubscribers(docId, json);
        // 通知全局订阅者
        sendToSubscribers(0L, json);
    }

    private void sendToSubscribers(Long docId, String json) {
        var list = subscribers.get(docId);
        if (list == null) return;
        for (var emitter : list) {
            try {
                emitter.send(SseEmitter.event().data(json));
            } catch (IOException e) {
                list.remove(emitter);
            }
        }
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
