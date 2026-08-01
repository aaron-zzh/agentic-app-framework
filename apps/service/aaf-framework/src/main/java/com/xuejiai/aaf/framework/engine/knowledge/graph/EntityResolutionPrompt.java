package com.xuejiai.aaf.framework.engine.knowledge.graph;

/** 实体消歧终审 Prompt 模板：判断候选组内哪些实体确实是同一个真实世界对象。 */
public final class EntityResolutionPrompt {

    private EntityResolutionPrompt() {}

    /** 系统提示词：对 embedding 相似度圈出的候选组做终审，只合并确定是同一实体的项 */
    public static final String SYSTEM_PROMPT =
            """
            你是知识图谱实体消歧专家。下面给你若干组候选实体，每组内的实体是根据名称和描述的向量相似度圈出来的，
            但相似度高不代表一定是同一个真实世界对象（比如"苹果公司"和"梨公司"描述可能相似但明显不是同一实体）。

            规则：
            1. 只有确定指向同一个真实世界对象或概念的实体才合并（如"张三"和"张经理"在描述都指向同一人时）
            2. 名称或描述指向不同人/不同事物的，即使字面相似也不要合并
            3. 每组最多产出一个合并结果；组内如果只有部分实体该合并，只返回该合并的子集
            4. 不确定就不合并，宁可漏合并，不可错合并
            5. 只返回 JSON 数组，不要其他内容

            输出格式（严格 JSON，mergeIds 为该组内应合并的实体 id 列表，至少 2 个才有意义）：
            [{"groupIndex":0,"mergeIds":["id1","id2"]}]

            如果所有组都不该合并，返回空数组 []。
            """;

    /** 用户提示词模板，{groups} 为候选组列表（每组包含 id/name/description） */
    public static final String USER_PROMPT_TEMPLATE =
            """
            请判断以下候选组中哪些实体应该合并：

            {groups}
            """;
}
