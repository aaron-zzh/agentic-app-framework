package com.xuejiai.aaf.autodev.doc.service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import com.xuejiai.aaf.autodev.doc.domain.AutodevDoc;
import com.xuejiai.aaf.autodev.doc.domain.AutodevDocLink;
import com.xuejiai.aaf.autodev.doc.domain.AutodevDocNode;
import com.xuejiai.aaf.autodev.doc.repository.AutodevDocLinkRepository;
import com.xuejiai.aaf.autodev.doc.repository.AutodevDocRelationRepository;
import com.xuejiai.aaf.autodev.doc.repository.AutodevDocRepository;

/** 开发文档导入服务：扫描 docs/ 目录，同步到 autodev_doc 表，提取引用关系写入 Neo4j。 */
@Service
public class AutodevDocImportService {

    private static final Logger log = LoggerFactory.getLogger(AutodevDocImportService.class);

    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");
    private static final Pattern MDLINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+\\.md[^)]*)\\)");

    private final AutodevDocRepository docRepository;
    private final AutodevDocLinkRepository docLinkRepository;
    private final AutodevDocRelationRepository docRelationRepository;

    public AutodevDocImportService(
            AutodevDocRepository docRepository,
            AutodevDocLinkRepository docLinkRepository,
            AutodevDocRelationRepository docRelationRepository) {
        this.docRepository = docRepository;
        this.docLinkRepository = docLinkRepository;
        this.docRelationRepository = docRelationRepository;
    }

    /** 启动时自动触发全量导入。 */
    @EventListener(ApplicationStartedEvent.class)
    public void onStartup() {
        log.info("启动时自动扫描 docs/ 目录（autodev_doc）");
        importAll();
    }

    /** 全量导入（手动触发或启动时调用）。 */
    @Transactional
    public int importAll() {
        Path docsRoot = resolveDocsRoot();
        if (!Files.exists(docsRoot)) {
            log.warn("docs/ 目录不存在：{}", docsRoot);
            return 0;
        }

        List<Path> mdFiles = collectMarkdownFiles(docsRoot);
        int count = 0;
        for (Path file : mdFiles) {
            try {
                importFile(file, docsRoot);
                count++;
            } catch (Exception e) {
                log.warn("导入文件失败：{}，原因：{}", file, e.getMessage());
            }
        }
        log.info("开发文档导入完成，共处理 {} 个文件", count);
        extractLinks();
        return count;
    }

    /** 提取链接关系（所有文档导入后执行）。 */
    @Transactional
    public void extractLinks() {
        List<AutodevDoc> docs = docRepository.findByStatusOrderByFilePath("active");
        for (AutodevDoc doc : docs) {
            if (doc.getContent() == null) continue;
            try {
                extractDocLinks(doc, docs);
            } catch (Exception e) {
                log.warn("提取链接失败：{}，原因：{}", doc.getFilePath(), e.getMessage());
            }
        }
    }

    private void importFile(Path file, Path docsRoot) throws IOException {
        String rawContent = Files.readString(file);
        String relativePath = docsRoot.getParent().relativize(file).toString().replace('\\', '/');

        Map<String, Object> frontMatter = parseFrontMatter(rawContent);
        String title = extractTitle(frontMatter, rawContent, file);
        String docType = inferDocType(relativePath);

        AutodevDoc doc = docRepository.findByFilePath(relativePath).orElse(new AutodevDoc());
        doc.setFilePath(relativePath);
        doc.setTitle(title);
        doc.setContent(rawContent);
        doc.setDocType(docType);
        doc.setStatus("active");
        doc.setFrontMatter(frontMatter);
        docRepository.save(doc);

        AutodevDocNode node =
                docRelationRepository
                        .findByDocId(doc.getId())
                        .orElse(new AutodevDocNode(doc.getId(), title, relativePath));
        node.setTitle(title);
        node.setFilePath(relativePath);
        docRelationRepository.save(node);
    }

    private void extractDocLinks(AutodevDoc source, List<AutodevDoc> allDocs) {
        docLinkRepository.deleteBySourceId(source.getId());
        String content = source.getContent();
        Set<Long> linked = new HashSet<>();

        Matcher wm = WIKILINK.matcher(content);
        while (wm.find()) {
            String name = wm.group(1).trim();
            allDocs.stream()
                    .filter(d -> d.getTitle().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(target -> {
                        if (linked.add(target.getId())) saveLink(source.getId(), target.getId(), "wikilink");
                    });
        }

        Matcher mm = MDLINK.matcher(content);
        while (mm.find()) {
            String resolved = resolveLinkPath(mm.group(1).trim());
            allDocs.stream()
                    .filter(d -> d.getFilePath() != null && d.getFilePath().endsWith(resolved))
                    .findFirst()
                    .ifPresent(target -> {
                        if (linked.add(target.getId())) saveLink(source.getId(), target.getId(), "mdlink");
                    });
        }
    }

    private void saveLink(Long sourceId, Long targetId, String linkType) {
        var link = new AutodevDocLink();
        link.setSourceId(sourceId);
        link.setTargetId(targetId);
        link.setLinkType(linkType);
        docLinkRepository.save(link);
        docRelationRepository.mergeRelation(sourceId, targetId, linkType);
    }

    private Path resolveDocsRoot() {
        return Path.of(System.getProperty("aaf.docs.path", "docs")).toAbsolutePath();
    }

    private List<Path> collectMarkdownFiles(Path root) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".md")) files.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("扫描目录失败：{}", e.getMessage());
        }
        return files;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontMatter(String content) {
        if (!content.startsWith("---")) return Map.of();
        int end = content.indexOf("---", 3);
        if (end < 0) return Map.of();
        try {
            Object parsed = new Yaml().load(content.substring(3, end).trim());
            return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String extractTitle(Map<String, Object> fm, String content, Path file) {
        if (fm.containsKey("purpose")) return String.valueOf(fm.get("purpose"));
        for (String line : content.split("\n")) {
            if (line.startsWith("# ")) return line.substring(2).trim();
        }
        String name = file.getFileName().toString();
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private String inferDocType(String path) {
        if (path.contains("/design/")) return "design";
        if (path.contains("/task/")) return "task";
        if (path.contains("/guide/")) return "guide";
        if (path.contains("/reference/")) return "reference";
        if (path.contains("/explanation/")) return "explanation";
        return "spec";
    }

    private String resolveLinkPath(String linkPath) {
        int hash = linkPath.indexOf('#');
        if (hash >= 0) linkPath = linkPath.substring(0, hash);
        return linkPath.replaceAll(".*/", "");
    }
}
