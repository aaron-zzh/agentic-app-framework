package com.xuejiai.aaf.framework.intelligent.ai.speech;

import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.core.AiUsage;

/**
 * ASR 流式识别结果帧。
 *
 * <p>普通帧：{@code text} 为识别文本，{@code usage} 为 null。 最后一帧（isCompleteResult）：{@code text} 可为空，{@code
 * usage} 非 null，携带本次识别总时长（毫秒）。
 */
public record AsrResult(String text, AiUsage usage) {

    /** 普通识别帧。 */
    public static AsrResult ofText(String text) {
        return new AsrResult(text, null);
    }

    /** 最终计费帧（isCompleteResult），携带 durationMs。 */
    public static AsrResult ofUsage(int durationMs) {
        AiUsage u =
                new AiUsage() {
                    @Override
                    public Map<String, Object> standardUsage() {
                        int secs = Math.max(1, (int) Math.ceil(durationMs / 1000.0));
                        return Map.of("duration", secs);
                    }

                    @Override
                    public Map<String, Object> rawUsage() {
                        return Map.of("durationMs", durationMs);
                    }
                };
        return new AsrResult(null, u);
    }

    /** 是否为计费帧。 */
    public boolean hasUsage() {
        return usage != null;
    }
}
