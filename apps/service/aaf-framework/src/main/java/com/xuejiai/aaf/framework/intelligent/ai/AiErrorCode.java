package com.xuejiai.aaf.framework.intelligent.ai;

import com.xuejiai.aaf.common.exception.ErrorCode;

/** AI 模块通用错误码，使用 1_005_000 ~ 1_005_999 段。 */
public interface AiErrorCode {

    // ========== 图像输入 1_005_100 ==========
    ErrorCode IMAGE_SOURCE_MISSING = ErrorCode.of(1_005_100, "图像来源不能为空，请提供 imageUrl 或 imageBase64");
    ErrorCode IMAGE_FORMAT_NOT_SUPPORTED = ErrorCode.of(1_005_101, "图像格式不支持，请使用 {0} 格式");
    ErrorCode IMAGE_BASE64_TOO_LARGE = ErrorCode.of(1_005_102, "Base64 图像编码后不得超过 {0}MB");
    ErrorCode IMAGE_URL_TOO_LARGE = ErrorCode.of(1_005_103, "图像文件不得超过 {0}MB");

    // ========== OCR 1_005_200 ==========
    ErrorCode OCR_RECOGNIZE_FAILED = ErrorCode.of(1_005_200, "OCR 识别失败：{0}");
    ErrorCode OCR_RESULT_SCHEMA_INVALID = ErrorCode.of(1_005_201, "resultSchema 格式不正确，须为合法 JSON");
}
