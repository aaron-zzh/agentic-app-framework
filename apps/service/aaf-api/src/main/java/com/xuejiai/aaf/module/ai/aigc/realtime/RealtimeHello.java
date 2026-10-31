package com.xuejiai.aaf.module.ai.aigc.realtime;

/** v1 hello 的受限字段。 */
public record RealtimeHello(
        String protocol, String endpointing, String roleKey, Boolean knowledge) {

    public boolean knowledgeEnabled() {
        return knowledge == null || knowledge;
    }
}
