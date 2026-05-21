package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/** 导入器工厂，按文件扩展名匹配对应的 DocumentImporter */
@Component
public class ImporterFactory {

    private final Map<String, DocumentImporter> importerMap;

    public ImporterFactory(List<DocumentImporter> importers) {
        this.importerMap =
                importers.stream()
                        .flatMap(
                                imp ->
                                        imp.supportedTypes().stream()
                                                .map(type -> Map.entry(type, imp)))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** 根据文件名获取对应的导入器 */
    public Optional<DocumentImporter> getImporter(String filename) {
        var ext = extractExtension(filename);
        return Optional.ofNullable(importerMap.get(ext));
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
