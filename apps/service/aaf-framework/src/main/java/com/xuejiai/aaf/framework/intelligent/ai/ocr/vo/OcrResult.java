package com.xuejiai.aaf.framework.intelligent.ai.ocr.vo;

import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.core.AiUsage;

/**
 * OCR 识别结果。
 *
 * @param text 模型原始 text 字段（通用识别为纯文字；高精识别为含坐标的 processed_text JSON）
 * @param ocrResult 精简坐标数据 JSON（[{text, box}] 格式，供前端画框用；部分任务才有）
 * @param inputTokens 输入 token 数
 * @param outputTokens 输出 token 数
 */
public record OcrResult(String text, String ocrResult, long inputTokens, long outputTokens)
        implements AiUsage {

    public static OcrResult ofText(String text) {
        return new OcrResult(text, null, 0L, 0L);
    }

    public static OcrResult ofText(String text, long inputTokens, long outputTokens) {
        return new OcrResult(text, null, inputTokens, outputTokens);
    }

    public static OcrResult ofStructured(
            String text, String ocrResult, long inputTokens, long outputTokens) {
        return new OcrResult(text, ocrResult, inputTokens, outputTokens);
    }

    @Override
    public Map<String, Object> standardUsage() {
        return Map.of("inputTokens", inputTokens, "outputTokens", outputTokens);
    }
}
