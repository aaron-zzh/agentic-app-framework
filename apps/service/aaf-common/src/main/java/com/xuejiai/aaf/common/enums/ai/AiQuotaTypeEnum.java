package com.xuejiai.aaf.common.enums.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 模型配额计费类型枚举。
 *
 * <p>对应 ai_model.quota_type 字段：0=按 token 计费，1=按次计费。
 */
@Getter
@AllArgsConstructor
public enum AiQuotaTypeEnum {
    TOKEN(0, "按 token 计费"),
    PER_USE(1, "按次计费");

    private final int code;
    private final String label;

    public static AiQuotaTypeEnum of(int code) {
        for (AiQuotaTypeEnum e : values()) {
            if (e.code == code) return e;
        }
        throw new IllegalArgumentException("未知 AiQuotaType: " + code);
    }
}
