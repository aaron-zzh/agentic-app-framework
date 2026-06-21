package com.xuejiai.aaf.module.ai.vision;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.ai.vision.VisionAttachment;
import com.xuejiai.aaf.framework.intelligent.ai.vision.VisionAttachment.AttachmentType;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.system.file.repository.FileRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 视觉附件解析器——把前端传来的 fileKey 列表转换为带签名 URL 与 MIME 元信息的 {@link VisionAttachment}。
 *
 * <p>职责单一：
 *
 * <ul>
 *   <li>从 {@code sys_file} 读取 fileKey 对应的 mimeType（缺失即拒绝，避免下游 AI 模型下载失败）
 *   <li>调 {@link StorageService#getPresignedDownloadUrl} 生成 1 小时有效期的 OSS 签名 GET URL
 *   <li>按 mimeType 前缀分类为 IMAGE 或 VIDEO
 * </ul>
 *
 * <p>本组件不感知具体业务（文案生成、计划助理、聊天等），由各业务在调用 AI 前调用该组件统一转换。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisionMediaResolver {

    /** 签名 URL 默认有效期：1 小时。视觉模型典型调用 < 1 分钟，留余量给重试与并行调用。 */
    private static final Duration DEFAULT_EXPIRY = Duration.ofHours(1);

    private final StorageService storageService;
    private final FileRecordRepository fileRecordRepository;

    /**
     * 批量解析 fileKey 列表为 {@link VisionAttachment}。
     *
     * @param fileKeys OSS 内部 key 列表，可为空
     * @return 解析后的附件列表，与入参一一对应；入参为空返回空列表
     * @throws BusinessException fileKey 不存在或 mimeType 缺失时抛出
     */
    public List<VisionAttachment> resolve(List<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) return List.of();
        return fileKeys.stream().map(this::resolveOne).filter(Objects::nonNull).toList();
    }

    private VisionAttachment resolveOne(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) return null;
        var record =
                fileRecordRepository
                        .findByKey(fileKey)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "文件不存在: " + fileKey));
        var mime = record.getMimeType();
        if (mime == null || mime.isBlank()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "文件 mimeType 缺失，无法用于视觉理解: " + fileKey);
        }
        var url = storageService.getPresignedDownloadUrl(fileKey, DEFAULT_EXPIRY);
        var type = mime.startsWith("video/") ? AttachmentType.VIDEO : AttachmentType.IMAGE;
        log.debug("视觉附件解析: fileKey={}, mime={}, type={}", fileKey, mime, type);
        return new VisionAttachment(fileKey, mime, url, type);
    }
}
