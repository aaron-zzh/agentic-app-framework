package com.xuejiai.aaf.module.ai.aigc.realtime;

import java.util.Set;

/** v1 hello 的受限字段。 */
public record RealtimeHello(
        String protocol,
        String endpointing,
        String roleKey,
        Boolean knowledge,
        String voice,
        Double temperature) {

    private static final Set<String> WEB_VOICES =
            Set.of("Tina", "Serena", "Ethan", "Harvey", "Momo");

    public RealtimeHello {
        if (voice != null && !WEB_VOICES.contains(voice)) {
            throw new IllegalArgumentException("voice 不受支持");
        }
        if (temperature != null
                && (!Double.isFinite(temperature) || temperature < 0 || temperature >= 2)) {
            throw new IllegalArgumentException("temperature 必须在 [0,2) 范围内");
        }
    }

    public boolean knowledgeEnabled() {
        return knowledge == null || knowledge;
    }
}
