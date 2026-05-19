package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 文档导入器接口，将各种格式解析为统一的内部中间表示
 */
public interface DocumentImporter {

    /** 支持的文件扩展名 */
    Set<String> supportedTypes();

    /** 解析文档 */
    ImportResult importDocument(InputStream input, String filename) throws IOException;
}
