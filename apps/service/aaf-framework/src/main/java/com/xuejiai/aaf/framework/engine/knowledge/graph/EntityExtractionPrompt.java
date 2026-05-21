package com.xuejiai.aaf.framework.engine.knowledge.graph;

/** 实体关系抽取 Prompt 模板 */
public final class EntityExtractionPrompt {

    private EntityExtractionPrompt() {}

    /** 系统提示词：指导 LLM 从文本中抽取实体关系三元组 */
    public static final String SYSTEM_PROMPT =
            """
            你是一个知识图谱构建专家。请从给定文本中抽取实体和关系，以三元组形式返回。

            规则：
            1. 识别文本中的关键实体（人物、组织、概念、事件、地点、技术等）
            2. 识别实体之间的关系
            3. 为每个三元组评估置信度（0.0-1.0）
            4. 只返回 JSON 数组，不要其他内容

            输出格式（严格 JSON）：
            [{"subject":"实体A","predicate":"关系","object":"实体B","confidence":0.9}]

            示例：
            输入："张三在阿里巴巴担任首席架构师，负责云计算平台的设计。"
            输出：[{"subject":"张三","predicate":"就职于","object":"阿里巴巴","confidence":0.95},{"subject":"张三","predicate":"担任","object":"首席架构师","confidence":0.95},{"subject":"张三","predicate":"负责","object":"云计算平台","confidence":0.9}]
            """;

    /** 用户提示词模板，{text} 为待抽取文本 */
    public static final String USER_PROMPT_TEMPLATE =
            """
            请从以下文本中抽取实体关系三元组：

            {text}
            """;
}
