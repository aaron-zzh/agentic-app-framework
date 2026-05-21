package com.xuejiai.aaf.common.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import lombok.experimental.UtilityClass;

/** 文件工具类，提供文件类型检测、唯一文件名生成、临时文件创建等便捷方法。 */
@UtilityClass
public class FileUtils {

    /**
     * 生成唯一文件名，保留原始扩展名。
     *
     * <p>示例：{@code generateUniqueFilename("avatar.png")} → {@code "a1b2c3d4-....png"}
     *
     * @param originalFilename 原始文件名
     * @return UUID 文件名
     */
    public static String generateUniqueFilename(String originalFilename) {
        var ext = getExtension(originalFilename);
        return ext.isEmpty() ? UUID.randomUUID().toString() : UUID.randomUUID() + "." + ext;
    }

    /**
     * 获取文件扩展名（不含点，小写）。
     *
     * <p>示例：{@code getExtension("photo.JPG")} → {@code "jpg"}
     *
     * @param filename 文件名
     * @return 扩展名，无扩展名返回空字符串
     */
    public static String getExtension(String filename) {
        if (filename == null || filename.isBlank()) return "";
        var idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).toLowerCase();
    }

    /**
     * 获取不含扩展名的文件名。
     *
     * <p>示例：{@code getBaseName("report.pdf")} → {@code "report"}
     */
    public static String getBaseName(String filename) {
        if (filename == null || filename.isBlank()) return "";
        var idx = filename.lastIndexOf('.');
        return idx < 0 ? filename : filename.substring(0, idx);
    }

    /**
     * 创建临时文件（JVM 退出时自动删除）。
     *
     * @param data 文件内容
     * @return 临时文件
     */
    public static File createTempFile(byte[] data) throws IOException {
        var file = createTempFile();
        Files.write(file.toPath(), data);
        return file;
    }

    /**
     * 创建空临时文件（JVM 退出时自动删除）。
     *
     * @return 临时文件
     */
    public static File createTempFile() throws IOException {
        var file = File.createTempFile(UUID.randomUUID().toString(), null);
        file.deleteOnExit();
        return file;
    }

    /**
     * 根据扩展名判断是否为图片。
     *
     * @param filename 文件名
     * @return 是否为图片
     */
    public static boolean isImage(String filename) {
        var ext = getExtension(filename);
        return switch (ext) {
            case "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> true;
            default -> false;
        };
    }
}
