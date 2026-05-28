package com.xuejiai.aaf.autodev.doc.service;

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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.autodev.doc.domain.AutodevDoc;
import com.xuejiai.aaf.autodev.doc.domain.AutodevDocLink;
import com.xuejiai.aaf.autodev.doc.repository.AutodevDocLinkRepository;
import com.xuejiai.aaf.autodev.doc.repository.AutodevDocRepository;
import com.xuejiai.aaf.autodev.doc.vo.*;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/** 开发文档管理服务。 */
@Service
public class AutodevDocService {

    private static final Logger log = LoggerFactory.getLogger(AutodevDocService.class);
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    /** SSE 订阅者注册表：docId=0 表示全局订阅 */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> subscribers =
            new ConcurrentHashMap<>();

    private final AutodevDocRepository docRepository;
    private final AutodevDocLinkRepository docLinkRepository;
    private final AutodevDocImportService importService;

    public AutodevDocService(
            AutodevDocRepository docRepository,
            AutodevDocLinkRepository docLinkRepository,
            AutodevDocImportService importService) {
        this.docRepository = docRepository;
        this.docLinkRepository = docLinkRepository;
        this.importService = importService;
    }

    public List<AutodevDocTreeNodeVO> getTree() {
        return buildTree(docRepository.findByStatusOrderByFilePath("active"));
    }

    public AutodevDoc getById(Long id) {
        return docRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "文档不存在"));
    }

    @Transactional
    public AutodevDoc update(Long id, String content) {
        AutodevDoc doc = getById(id);
        doc.setContent(content);
        docRepository.save(doc);
        if (doc.getFilePath() != null) writeToLocalFile(doc.getFilePath(), content);
        importService.extractLinks();
        broadcastChange(id, doc.getTitle());
        return doc;
    }

    @Transactional
    public AutodevDoc create(AutodevDocCreateDTO dto) {
        String filePath = dto.filePath();
        if (!filePath.startsWith("docs/")) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文件路径必须以 docs/ 开头");
        }
        // 防路径穿越
        Path normalized = Path.of(filePath).normalize();
        if (!normalized.toString().replace('\\', '/').startsWith("docs/")) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "非法文件路径");
        }

        String content = dto.content() != null ? dto.content() : "";
        writeToLocalFile(filePath, content);

        AutodevDoc doc = new AutodevDoc();
        doc.setTitle(dto.title());
        doc.setFilePath(filePath);
        doc.setContent(content);
        doc.setDocType(dto.docType() != null ? dto.docType() : "spec");
        doc.setStatus("active");
        return docRepository.save(doc);
    }

    public List<AutodevDocSearchResultVO> search(String query) {
        return docRepository.fullTextSearch(query).stream()
                .map(
                        d ->
                                new AutodevDocSearchResultVO(
                                        d.getId(),
                                        d.getTitle(),
                                        d.getFilePath(),
                                        extractSnippet(d.getContent(), query)))
                .collect(Collectors.toList());
    }

    public AutodevDocRelationGraphVO getRelations(Long id) {
        getById(id);
        List<AutodevDocLink> outgoing = docLinkRepository.findBySourceId(id);
        List<AutodevDocLink> incoming = docLinkRepository.findByTargetId(id);

        Set<Long> nodeIds = new HashSet<>();
        nodeIds.add(id);
        outgoing.forEach(l -> nodeIds.add(l.getTargetId()));
        incoming.forEach(l -> nodeIds.add(l.getSourceId()));

        List<AutodevDoc> nodes = docRepository.findAllById(nodeIds);
        List<AutodevDocRelationGraphVO.Edge> edges = new ArrayList<>();
        outgoing.forEach(
                l ->
                        edges.add(
                                new AutodevDocRelationGraphVO.Edge(
                                        l.getSourceId(), l.getTargetId(), l.getLinkType())));
        incoming.forEach(
                l ->
                        edges.add(
                                new AutodevDocRelationGraphVO.Edge(
                                        l.getSourceId(), l.getTargetId(), l.getLinkType())));

        List<AutodevDocRelationGraphVO.Node> graphNodes =
                nodes.stream()
                        .map(
                                d ->
                                        new AutodevDocRelationGraphVO.Node(
                                                d.getId(),
                                                d.getTitle(),
                                                d.getFilePath(),
                                                d.getId().equals(id)))
                        .collect(Collectors.toList());

        return new AutodevDocRelationGraphVO(graphNodes, edges);
    }

    /** 订阅文档变更 SSE 事件。docId=0 表示订阅所有文档变更。 */
    public SseEmitter subscribe(Long docId) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var list = subscribers.computeIfAbsent(docId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove =
                () -> {
                    list.remove(emitter);
                    if (list.isEmpty()) subscribers.remove(docId, list);
                };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    private void broadcastChange(Long docId, String title) {
        String json =
                "{\"type\":\"doc_updated\",\"docId\":%d,\"title\":\"%s\"}"
                        .formatted(docId, title.replace("\"", "\\\""));
        // 精确订阅者 + 全局订阅者
        for (Long key : List.of(docId, 0L)) {
            var list = subscribers.get(key);
            if (list == null) continue;
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().data(json));
                } catch (IOException e) {
                    log.debug("SSE 发送失败（客户端已断开）");
                }
            }
        }
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

    private List<AutodevDocTreeNodeVO> buildTree(List<AutodevDoc> docs) {
        Map<String, AutodevDocTreeNodeVO> dirMap = new LinkedHashMap<>();
        List<AutodevDocTreeNodeVO> roots = new ArrayList<>();

        for (AutodevDoc doc : docs) {
            String path = doc.getFilePath() != null ? doc.getFilePath() : doc.getTitle();
            String[] parts = path.split("/");
            AutodevDocTreeNodeVO parent = null;
            var currentPath = new StringBuilder();

            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) currentPath.append("/");
                currentPath.append(parts[i]);
                String dirPath = currentPath.toString();
                final int idx = i;
                final AutodevDocTreeNodeVO parentRef = parent;
                AutodevDocTreeNodeVO dir =
                        dirMap.computeIfAbsent(
                                dirPath,
                                k -> {
                                    var node =
                                            new AutodevDocTreeNodeVO(
                                                    null, parts[idx], k, true, new ArrayList<>());
                                    if (parentRef == null) roots.add(node);
                                    else parentRef.children().add(node);
                                    return node;
                                });
                parent = dir;
            }

            var leaf =
                    new AutodevDocTreeNodeVO(doc.getId(), doc.getTitle(), path, false, List.of());
            if (parent != null) parent.children().add(leaf);
            else roots.add(leaf);
        }
        return roots;
    }
}
