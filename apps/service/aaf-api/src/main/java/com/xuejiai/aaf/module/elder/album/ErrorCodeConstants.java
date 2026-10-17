package com.xuejiai.aaf.module.elder.album;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** 养老相册错误码，使用 10_800_000 ~ 10_899_999 段。 */
public interface ErrorCodeConstants {
    ErrorCode PHOTO_NOT_FOUND = ErrorCode.of(10_800_000, 404, "照片不存在");
    ErrorCode PHOTO_INVALID = ErrorCode.of(10_800_001, "照片信息不合法");
    ErrorCode PHOTO_FORMAT_UNSUPPORTED = ErrorCode.of(10_800_002, "仅支持 JPEG、PNG、WebP 图片");
    ErrorCode PHOTO_TOO_LARGE = ErrorCode.of(10_800_003, "照片大小不能超过 10 MiB");
    ErrorCode PHOTO_VERSION_CONFLICT = ErrorCode.of(10_800_004, 409, "照片已被其他请求修改，请刷新后重试");
    ErrorCode PHOTO_CLIENT_ID_CONFLICT = ErrorCode.of(10_800_005, 409, "同一设备照片编号对应了不同内容");
}
