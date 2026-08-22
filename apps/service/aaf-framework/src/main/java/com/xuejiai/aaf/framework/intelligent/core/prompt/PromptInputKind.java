package com.xuejiai.aaf.framework.intelligent.core.prompt;

/** 调用前长度遥测使用的输入分类；分类不改变消息信任级别。 */
public enum PromptInputKind {
    SYSTEM,
    CURRENT_USER_INPUT,
    OTHER_USER_INPUT,
    ASSISTANT_HISTORY,
    /** 推理模型的加密推理块回放；不可裁剪、不可改写。 */
    ASSISTANT_REASONING,
    CONTROLLED_CONTEXT,
    TOOL_RESULT,
    TOOL_REFERENCE,
    TOOL_DEFINITION
}
