package com.xuejiai.aaf.framework.intelligent.ai.ocr.vo;

/**
 * OCR 识别结果。
 *
 * @param text 模型原始 text 字段（通用识别为纯文字；高精识别为含坐标的 processed_text JSON）
 * @param ocrResult 精简坐标数据 JSON（[{text, box}] 格式，供前端画框用；部分任务才有）
 * @param inputTokens 输入 token 数
 * @param outputTokens 输出 token 数
 */
public record OcrResult(String text, String ocrResult, int inputTokens, int outputTokens) {

    public static OcrResult ofText(String text) {
        return new OcrResult(text, null, 0, 0);
    }

    public static OcrResult ofText(String text, int inputTokens, int outputTokens) {
        return new OcrResult(text, null, inputTokens, outputTokens);
    }

    public static OcrResult ofStructured(
            String text, String ocrResult, int inputTokens, int outputTokens) {
        return new OcrResult(text, ocrResult, inputTokens, outputTokens);
    }
}
