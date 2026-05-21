package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.util.Map;

/**
 * 文档段落，统一的内部中间表示
 *
 * @param content 段落文本
 * @param level 标题层级（0=正文，1=h1，2=h2...）
 * @param metadata 元数据（页码、标题等）
 */
public record DocumentSection(String content, int level, Map<String, Object> metadata) {}
