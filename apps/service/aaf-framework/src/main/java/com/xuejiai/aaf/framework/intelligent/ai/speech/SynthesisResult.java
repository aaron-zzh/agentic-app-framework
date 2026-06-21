package com.xuejiai.aaf.framework.intelligent.ai.speech;

import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.core.AiUsage;

/**
 * TTS 合成结果，携带音频字节和计费用量。
 *
 * <p>quotaType=TOKEN：inputTokens = 输入文本字符数，outputTokens = 0。
 */
public record SynthesisResult(byte[] audio, int charCount) implements AiUsage {

    @Override
    public Map<String, Object> standardUsage() {
        return Map.of("inputTokens", (long) charCount, "outputTokens", 0L);
    }
}
