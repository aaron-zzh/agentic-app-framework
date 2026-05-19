package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.util.List;

/**
 * 文档导入结果
 *
 * @param sections        解析后的段落列表
 * @param title           文档标题
 * @param totalCharacters 总字符数
 */
public record ImportResult(List<DocumentSection> sections, String title, long totalCharacters) {}
