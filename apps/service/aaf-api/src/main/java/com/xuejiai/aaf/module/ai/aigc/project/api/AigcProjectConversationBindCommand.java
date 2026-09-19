package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 将项目绑定到当前用户可执行 Assistant 并创建受控项目会话。 */
public record AigcProjectConversationBindCommand(
        Long projectId, Integer expectedProjectVersion, String assistantId) {

    public AigcProjectConversationBindCommand {
        if (projectId == null || projectId < 1) {
            throw new IllegalArgumentException("projectId 必须为正数");
        }
        if (expectedProjectVersion == null || expectedProjectVersion < 0) {
            throw new IllegalArgumentException("expectedProjectVersion 不能为空或为负数");
        }
        if (assistantId == null || assistantId.isBlank()) {
            throw new IllegalArgumentException("assistantId 不能为空白");
        }
        assistantId = assistantId.trim();
    }
}
