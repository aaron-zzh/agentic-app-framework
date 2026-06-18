package com.xuejiai.aaf.framework.intelligent.ai.ocr.vo;

/**
 * OCR 识别请求。
 *
 * @param imageUrl 图像 URL（与 imageBase64 二选一）
 * @param imageBase64 Base64 编码的图像（与 imageUrl 二选一），格式：data:image/jpeg;base64,...
 * @param prompt 自定义提示词；为 null 时使用模型默认提示词（纯文本提取）
 * @param task 内置任务类型，见 {@link OcrTask}；为 null 时使用自定义 prompt
 * @param resultSchema 信息抽取的字段模板 JSON（task=KEY_INFORMATION_EXTRACTION 时有效）
 * @param enableRotate 是否开启图像自动转正（DashScope SDK 专有，OpenAI 兼容模式下忽略）
 * @param minPixels 图像最小像素阈值，默认 32*32*3
 * @param maxPixels 图像最大像素阈值，默认 32*32*8192
 * @param modelId 模型 ID，默认 qwen3.5-ocr
 * @param imageWidth 图像原始宽度（可选，前端已知时传入，用于精确积分预估）
 * @param imageHeight 图像原始高度（可选，前端已知时传入，用于精确积分预估）
 */
public record OcrRequest(
        String imageUrl,
        String imageBase64,
        String prompt,
        OcrTask task,
        String resultSchema,
        boolean enableRotate,
        int minPixels,
        int maxPixels,
        String modelId,
        Integer imageWidth,
        Integer imageHeight) {

    public static final String DEFAULT_MODEL = "qwen3.5-ocr";
    public static final int DEFAULT_MIN_PIXELS = 32 * 32 * 3;
    public static final int DEFAULT_MAX_PIXELS = 32 * 32 * 8192;

    /** 支持的图像格式后缀（URL 校验用） */
    private static final java.util.regex.Pattern SUPPORTED_FORMAT =
            java.util.regex.Pattern.compile(
                    ".*\\.(bmp|jpe|jpeg|jpg|png|tif|tiff|webp|heic)(\\?.*)?$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final String SUPPORTED_FORMAT_DESC = "BMP/JPEG/PNG/TIFF/WEBP/HEIC";

    /** Base64 编码后 10MB 上限（字符数） */
    private static final int MAX_BASE64_CHARS = 10 * 1024 * 1024;

    private static final int MAX_BASE64_MB = 10;

    /**
     * 前置校验：检查图像来源合法性。 所有调用路径（Controller / OcrTool / 直接调用）均应在 recognize() 前调用。
     *
     * @throws com.xuejiai.aaf.common.exception.BusinessException 校验不通过时抛出
     */
    public void validate() {
        boolean hasUrl = imageUrl != null && !imageUrl.isBlank();
        boolean hasBase64 = imageBase64 != null && !imageBase64.isBlank();

        if (!hasUrl && !hasBase64) {
            throw com.xuejiai.aaf.common.exception.ExceptionUtil.exception(
                    com.xuejiai.aaf.framework.intelligent.ai.AiErrorCode.IMAGE_SOURCE_MISSING);
        }
        if (hasUrl && !SUPPORTED_FORMAT.matcher(imageUrl).matches()) {
            throw com.xuejiai.aaf.common.exception.ExceptionUtil.exception(
                    com.xuejiai.aaf.framework.intelligent.ai.AiErrorCode.IMAGE_FORMAT_NOT_SUPPORTED,
                    SUPPORTED_FORMAT_DESC);
        }
        if (hasBase64 && imageBase64.length() > MAX_BASE64_CHARS) {
            throw com.xuejiai.aaf.common.exception.ExceptionUtil.exception(
                    com.xuejiai.aaf.framework.intelligent.ai.AiErrorCode.IMAGE_BASE64_TOO_LARGE,
                    MAX_BASE64_MB);
        }
    }

    /** 快捷构造：图像 URL + 自定义 prompt */
    public static OcrRequest ofUrl(String imageUrl, String prompt) {
        return new OcrRequest(
                imageUrl,
                null,
                prompt,
                null,
                null,
                false,
                DEFAULT_MIN_PIXELS,
                DEFAULT_MAX_PIXELS,
                DEFAULT_MODEL,
                null,
                null);
    }

    /** 快捷构造：图像 URL + 内置任务 */
    public static OcrRequest ofUrl(String imageUrl, OcrTask task) {
        return new OcrRequest(
                imageUrl,
                null,
                null,
                task,
                null,
                false,
                DEFAULT_MIN_PIXELS,
                DEFAULT_MAX_PIXELS,
                DEFAULT_MODEL,
                null,
                null);
    }

    /** 快捷构造：图像 URL，使用默认通用文字识别 */
    public static OcrRequest ofUrl(String imageUrl) {
        return ofUrl(imageUrl, OcrTask.TEXT_RECOGNITION);
    }

    /** 快捷构造：Base64 图像 + 自定义 prompt */
    public static OcrRequest ofBase64(String base64, String prompt) {
        return new OcrRequest(
                null,
                base64,
                prompt,
                null,
                null,
                false,
                DEFAULT_MIN_PIXELS,
                DEFAULT_MAX_PIXELS,
                DEFAULT_MODEL,
                null,
                null);
    }
}
