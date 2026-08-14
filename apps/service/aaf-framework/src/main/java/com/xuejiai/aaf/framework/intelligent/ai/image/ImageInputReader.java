package com.xuejiai.aaf.framework.intelligent.ai.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Base64;

/** 将模型参考图的 Data URL 或 HTTPS URL 解析为图片二进制。 */
public final class ImageInputReader {

    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;

    private ImageInputReader() {}

    /** 读取单张图片，拒绝非图片 Data URL 和非 HTTP(S) URL。 */
    public static ImageInput read(String value) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("参考图不能为空");
        }
        if (value.startsWith("data:")) {
            return readDataUrl(value);
        }
        var uri = URI.create(value);
        var scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IOException("参考图 URL 协议不受支持");
        }
        var connection = uri.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        try (var input = connection.getInputStream()) {
            return new ImageInput(readWithLimit(input), guessMimeType(uri.getPath()));
        }
    }

    private static ImageInput readDataUrl(String value) throws IOException {
        var commaIndex = value.indexOf(',');
        if (commaIndex <= "data:".length()) {
            throw new IOException("参考图 Data URL 格式无效");
        }
        var metadata = value.substring("data:".length(), commaIndex);
        if (!metadata.endsWith(";base64")) {
            throw new IOException("参考图 Data URL 必须使用 Base64 编码");
        }
        var mimeType = metadata.substring(0, metadata.length() - ";base64".length());
        if (!mimeType.startsWith("image/")) {
            throw new IOException("参考图 Data URL 不是图片类型");
        }
        var encodedData = value.substring(commaIndex + 1);
        if (encodedData.length() > ((long) MAX_IMAGE_BYTES + 2) * 4 / 3) {
            throw new IOException("参考图超过 20MB 限制");
        }
        try {
            var bytes = Base64.getDecoder().decode(encodedData);
            assertSize(bytes.length);
            return new ImageInput(bytes, mimeType);
        } catch (IllegalArgumentException e) {
            throw new IOException("参考图 Data URL Base64 无效", e);
        }
    }

    private static byte[] readWithLimit(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        var buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_IMAGE_BYTES) {
                throw new IOException("参考图超过 20MB 限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void assertSize(int size) throws IOException {
        if (size > MAX_IMAGE_BYTES) {
            throw new IOException("参考图超过 20MB 限制");
        }
    }

    private static String guessMimeType(String path) {
        var lower = path.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/png";
    }

    /** 已解析的图片内容。 */
    public record ImageInput(byte[] bytes, String mimeType) {

        /** 根据 MIME 类型生成可供 multipart 上传的文件扩展名。 */
        public String extension() {
            return switch (mimeType) {
                case "image/jpeg" -> "jpg";
                case "image/webp" -> "webp";
                case "image/gif" -> "gif";
                default -> "png";
            };
        }
    }
}
