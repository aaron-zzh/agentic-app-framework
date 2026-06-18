package com.xuejiai.aaf.framework.intelligent.ai.ocr.vo;

/**
 * Qwen-OCR 内置任务类型。
 *
 * <p>对应 DashScope SDK 的 {@code ocr_options.task} 参数值。
 */
public enum OcrTask {

    /** 高精识别：定位文字行并输出坐标，结果含 words_info 数组 */
    ADVANCED_RECOGNITION("advanced_recognition"),

    /** 信息抽取：从票据、证件等提取结构化 KV，结果在 kv_result 字段 */
    KEY_INFORMATION_EXTRACTION("key_information_extraction"),

    /** 表格解析：返回 HTML 格式 */
    TABLE_PARSING("table_parsing"),

    /** 文档解析：返回 LaTeX 格式 */
    DOCUMENT_PARSING("document_parsing"),

    /** 公式识别：返回 LaTeX 格式 */
    FORMULA_RECOGNITION("formula_recognition"),

    /** 通用文字识别：纯文本输出 */
    TEXT_RECOGNITION("text_recognition"),

    /** 多语言识别：纯文本输出 */
    MULTI_LAN("multi_lan");

    public final String value;

    OcrTask(String value) {
        this.value = value;
    }
}
