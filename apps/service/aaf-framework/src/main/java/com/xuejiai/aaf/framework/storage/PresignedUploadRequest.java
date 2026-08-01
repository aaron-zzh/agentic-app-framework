package com.xuejiai.aaf.framework.storage;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * 预签名上传请求（M29 / m20）。
 *
 * <p>M29：{@link StorageService#getPresignedUploadUrl} 原先接受调用方给的**裸 key**，owner 命名空间规则只存在于
 * API 层（{@code FileRecordService#requireCurrentOwnerNamespace}），新增调用方很容易误签到别人的命名空间。 现在把 key 生成收进框架：调用方只能给
 * {@code ownerScope + filename}，key 由 {@link #toKey()} 生成。
 *
 * <p>m20：同时强制携带 {@code contentType} 与 {@code maxSizeBytes}，由实现写入签名约束， 避免客户端拿到签名后上传任意类型/超大对象。
 *
 * @param ownerScope 归属命名空间，如 {@code users/42}；不可为空、不可含 {@code ..}
 * @param filename 原始文件名，仅用于取扩展名（不参与路径拼接，避免路径穿越）
 * @param contentType 允许的 MIME 类型，签名时固定
 * @param maxSizeBytes 允许的最大字节数，签名时固定
 * @param expiry 签名有效期
 */
public record PresignedUploadRequest(
        String ownerScope,
        String filename,
        String contentType,
        long maxSizeBytes,
        Duration expiry) {

    public PresignedUploadRequest {
        if (ownerScope == null || ownerScope.isBlank() || ownerScope.contains("..")) {
            throw new StorageException("非法的归属命名空间: " + ownerScope, null);
        }
        if (maxSizeBytes <= 0) {
            throw new StorageException("预签名上传必须指定正的大小上限", null);
        }
        // B13：主动内容不允许通过预签名直传绕过校验
        UploadPolicy.assertNotActiveContent(filename, contentType);
    }

    /** 服务端生成 key：{@code ownerScope/yyyy/MM/dd/uuid.ext}，客户端无法影响路径。 */
    public String toKey() {
        var date = LocalDate.now();
        return "%s/%d/%02d/%02d/%s%s"
                .formatted(
                        trimSlash(ownerScope),
                        date.getYear(),
                        date.getMonthValue(),
                        date.getDayOfMonth(),
                        UUID.randomUUID(),
                        extension());
    }

    private String extension() {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        var ext = filename.substring(dot).toLowerCase(Locale.ROOT);
        // 扩展名只允许字母数字，防止把路径分隔符或查询串带进 key
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }

    private static String trimSlash(String scope) {
        var s = scope.startsWith("/") ? scope.substring(1) : scope;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
