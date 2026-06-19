package com.xuejiai.aaf.common.enums.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 模型配额计费类型枚举。
 *
 * <p>对应 ai_model.quota_type 字段：
 *
 * <ul>
 *   <li>0=TOKEN：按 input/output token 分别计费
 *   <li>1=PER_USE：按次固定单价
 *   <li>2=PER_SEC：按时长（秒）计费，如音乐生成
 *   <li>3=PER_UNIT：按单元计费，单元由调用参数决定（如视频分辨率 720p/1080p）
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum AiQuotaTypeEnum {
    TOKEN(0, "按 token 计费"),
    PER_USE(1, "按次计费"),
    PER_SEC(2, "按秒计费"),
    PER_UNIT(3, "按单元计费");

    private final int code;
    private final String label;

    public static AiQuotaTypeEnum of(int code) {
        for (AiQuotaTypeEnum e : values()) {
            if (e.code == code) return e;
        }
        throw new IllegalArgumentException("未知 AiQuotaType: " + code);
    }
}
