package com.xuejiai.aaf.framework.engine.knowledge.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 文档魔数校验工具类——检测文件字节内容与声明的扩展名/MIME 是否一致。
 *
 * <p>只覆盖 AAF 支持的五类文档格式：PDF、DOCX、Markdown、HTML、TXT。纯文本容器（MD/HTML/TXT）没有固定魔数， 校验退化为 UTF-8
 * 可解码性检查；PDF/DOCX 有明确的二进制文件头，按魔数精确匹配。
 */
public final class DocumentMagicBytes {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {0x50, 0x4b, 0x03, 0x04};

    private static final Set<String> PLAIN_TEXT_EXTENSIONS =
            Set.of("md", "markdown", "html", "htm", "txt");

    private DocumentMagicBytes() {}

    /**
     * 校验文件内容是否与声明的扩展名一致。
     *
     * @param content 文件字节内容
     * @param extension 文件扩展名（不含点，小写或大写均可）
     * @return 一致返回 {@code true}；扩展名不受支持或内容不匹配返回 {@code false}
     */
    public static boolean matches(byte[] content, String extension) {
        if (content == null || extension == null) return false;
        var ext = extension.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "pdf" -> startsWith(content, PDF_MAGIC);
            case "docx" -> startsWith(content, ZIP_MAGIC);
            default -> PLAIN_TEXT_EXTENSIONS.contains(ext) && isUtf8Decodable(content);
        };
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) return false;
        for (var i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) return false;
        }
        return true;
    }

    /** 纯文本容器没有魔数，用 UTF-8 严格解码是否失败作为一致性信号。 */
    private static boolean isUtf8Decodable(byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(content));
            return true;
        } catch (java.nio.charset.CharacterCodingException failure) {
            return false;
        }
    }
}
