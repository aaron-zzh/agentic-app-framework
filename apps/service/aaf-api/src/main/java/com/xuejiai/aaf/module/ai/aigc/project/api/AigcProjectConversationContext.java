package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 项目页面与对话入口共用的服务端会话绑定投影。 */
public record AigcProjectConversationContext(
        Long projectId,
        Integer projectVersion,
        State state,
        Long assistantRecordId,
        String assistantId,
        Long conversationRecordId,
        String threadId) {

    public enum State {
        READY,
        NEEDS_ASSISTANT_BINDING
    }

    public static AigcProjectConversationContext bindingRequired(
            Long projectId, Integer projectVersion) {
        return new AigcProjectConversationContext(
                projectId, projectVersion, State.NEEDS_ASSISTANT_BINDING, null, null, null, null);
    }
}
