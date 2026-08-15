package com.xuejiai.aaf.framework.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 上传策略（B13）——类型、大小与主动内容的**唯一**校验入口。
 *
 * <p>所有应用层上传入口共享 {@link #validate}；各 {@link StorageClient} 实现在 upload 入口调用静态 {@link
 * #assertNotActiveContent} 兜底，防止绕过业务校验。URL/字节流读取通过 {@link #readWithLimit} 限制大小。
 */
public final class UploadPolicy {

    /** 可被浏览器解释执行的 MIME 类型——一律拒绝，避免存储型 XSS */
    private static final Set<String> ACTIVE_CONTENT_TYPES =
            Set.of(
                    "image/svg+xml",
                    "text/html",
                    "application/xhtml+xml",
                    "text/xml",
                    "application/xml",
                    "text/javascript",
                    "application/javascript",
                    "application/x-javascript",
                    "text/vbscript");

    /** 主动内容扩展名——contentType 可被伪造，扩展名同样要拦 */
    private static final Set<String> ACTIVE_CONTENT_EXTENSIONS =
            Set.of("svg", "html", "htm", "xhtml", "xml", "js", "mjs", "vbs", "swf");

    private final UploadLimits limits;

    public UploadPolicy(UploadLimits limits) {
        this.limits = limits;
    }

    /** 允许的最大字节数 */
    public long maxSizeBytes() {
        return limits.maxSizeBytes();
    }

    /**
     * 统一校验：大小 → 类型白名单 → 主动内容。
     *
     * @param filename 原始文件名或存储路径（用于扩展名判定）
     * @param contentType MIME 类型
     * @param size 字节数；未知时传 -1 跳过大小校验（此时调用方须用 {@link #readWithLimit} 限流读取）
     */
    public void validate(String filename, String contentType, long size) {
        if (size >= 0 && size > limits.maxSizeBytes()) {
            throw new StorageException(
                    "文件超过大小限制: %d > %d".formatted(size, limits.maxSizeBytes()), null);
        }
        if (!limits.allowedContentTypes().contains(contentType)) {
            throw new StorageException("不允许的文件类型: " + contentType, null);
        }
        assertNotActiveContent(filename, contentType);
    }

    /**
     * 拒绝主动内容（静态，供存储层直接兜底调用，无需注入）。
     *
     * <p>SVG/HTML 可携带 {@code <script>}，若对象与应用同域访问即形成存储型 XSS；这里直接拒绝而非净化，
     * 净化器本身是持续维护成本且容易被绕过。确有需求时应改为独立域名 + 强制 {@code Content-Disposition: attachment}。
     */
    public static void assertNotActiveContent(String filename, String contentType) {
        if (contentType != null
                && ACTIVE_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT).trim())) {
            throw new StorageException("拒绝可执行主动内容类型: " + contentType, null);
        }
        var ext = extensionOf(filename);
        if (ext != null && ACTIVE_CONTENT_EXTENSIONS.contains(ext)) {
            throw new StorageException("拒绝可执行主动内容扩展名: ." + ext, null);
        }
    }

    /**
     * 带上限读取输入流——超过上限立即失败，避免超大远程文件写入存储或撑爆内存。
     *
     * @throws StorageException 超过 {@link #maxSizeBytes()} 时抛出
     */
    public byte[] readWithLimit(InputStream input) throws IOException {
        long max = limits.maxSizeBytes();
        var buffer = new ByteArrayOutputStream();
        var chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(chunk)) != -1) {
            total += read;
            if (total > max) {
                throw new StorageException("文件超过大小限制: > %d".formatted(max), null);
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static String extensionOf(String filename) {
        if (filename == null) return null;
        // 去掉可能的 query 串再取扩展名
        var name = filename;
        int q = name.indexOf('?');
        if (q > 0) name = name.substring(0, q);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
