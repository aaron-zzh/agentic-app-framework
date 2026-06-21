package com.xuejiai.aaf.module.system.file.service;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.storage.FileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件上传门面服务——统一封装"物理上传 + sys_file 记录"，业务代码统一注入此类。
 *
 * <p>职责边界：
 *
 * <ul>
 *   <li>{@link FileService}：纯物理存储（aaf-framework 层），不写 DB
 *   <li>{@link FileRecordService}：sys_file 写入 + 配额校验（aaf-api 层）
 *   <li>本类：组合两者，是 aaf-api 内的唯一上传收口
 * </ul>
 *
 * <p>调用方传 {@code uploaderId=null} 表示系统生成文件（如 AIGC 素材），不做配额校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileService fileService;
    private final FileRecordService fileRecordService;

    /**
     * 从远程 URL 下载并上传，同时记录到 sys_file。
     *
     * @param url 远程文件 URL
     * @param path 存储路径，如 {@code aigc/image/xxx.png}
     * @param contentType MIME 类型
     * @param uploaderId 上传者 ID，系统生成传 null
     * @return 存储后的可访问 URL
     */
    public String uploadFromUrl(String url, String path, String contentType, Long uploaderId) {
        String result = fileService.uploadFromUrl(url, path, contentType);
        record(path, contentType, 0L, uploaderId);
        return result;
    }

    /**
     * 上传字节数组，同时记录到 sys_file。
     *
     * @param bytes 文件字节数组
     * @param path 存储路径，如 {@code aigc/voice/xxx.mp3}
     * @param contentType MIME 类型
     * @param uploaderId 上传者 ID，系统生成传 null
     * @return 存储后的可访问 URL
     */
    public String uploadFromBytes(byte[] bytes, String path, String contentType, Long uploaderId) {
        String result = fileService.uploadFromBytes(bytes, path, contentType);
        record(path, contentType, bytes.length, uploaderId);
        return result;
    }

    /**
     * 上传 base64 字符串（支持 {@code data:mime;base64,} 前缀），同时记录到 sys_file。
     *
     * @param b64 base64 字符串或 data URL
     * @param path 存储路径，如 {@code aigc/image/xxx.png}
     * @param uploaderId 上传者 ID，系统生成传 null
     * @return 存储后的可访问 URL
     */
    public String uploadFromBase64(String b64, String path, Long uploaderId) {
        String mime = parseMimeFromB64(b64);
        String result = fileService.uploadFromBase64(b64, path);
        long size = estimateB64Size(b64);
        record(path, mime, size, uploaderId);
        return result;
    }

    // ========== 内部工具 ==========

    private void record(String path, String mimeType, long size, Long uploaderId) {
        try {
            fileRecordService.save(path, path, mimeType, size, uploaderId);
        } catch (Exception e) {
            // 记录失败不影响主流程，但需要告警
            log.warn("[FileUploadService] sys_file 记录失败: path={}, err={}", path, e.getMessage());
        }
    }

    /** 从 data URL 前缀提取 MIME，无前缀返回 {@code application/octet-stream}。 */
    private String parseMimeFromB64(String b64) {
        if (b64 != null && b64.startsWith("data:")) {
            int comma = b64.indexOf(',');
            if (comma > 0) {
                String header = b64.substring(5, comma);
                return header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
            }
        }
        return "application/octet-stream";
    }

    /** 估算 base64 解码后的字节数（3/4 比例），避免完整解码浪费内存。 */
    private long estimateB64Size(String b64) {
        if (b64 == null) return 0L;
        String data = b64.contains(",") ? b64.substring(b64.indexOf(',') + 1) : b64;
        // base64 每 4 字符对应 3 字节，末尾 '=' 填充各减 1
        long len = data.length();
        long padding = data.endsWith("==") ? 2 : data.endsWith("=") ? 1 : 0;
        return len / 4 * 3 - padding;
    }
}
