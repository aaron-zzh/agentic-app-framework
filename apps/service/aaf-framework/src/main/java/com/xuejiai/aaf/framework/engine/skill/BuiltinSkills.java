package com.xuejiai.aaf.framework.engine.skill;

/**
 * 内置技能定义（代码权威来源）。
 * 应用启动时由 BuiltinSkillInitializer upsert 到数据库。
 * 用户可创建同名技能覆盖内置行为。
 */
public enum BuiltinSkills {

    /** 自我认知：介绍助理自身的人格、技能集、可用工具 */
    SELF_AWARENESS(
        "builtin-self-awareness",
        "自我认知",
        "介绍助理自身的人格、技能集和可用工具",
        null,
        "[\"你是谁\",\"你能做什么\",\"你有哪些技能\",\"介绍一下你自己\",\"你的能力\"]",
        "请介绍你自己的人格特点、拥有的技能和可以使用的工具。",
        "1.0.0",
        100
    ),

    /** 理解用户：主动收集用户画像（职业/偏好/沟通风格） */
    USER_UNDERSTANDING(
        "builtin-user-understanding",
        "理解用户",
        "主动收集用户背景、偏好和沟通风格，写入用户私有记忆",
        null,
        "[\"我是\",\"我的工作\",\"我喜欢\",\"我的背景\",\"了解我\"]",
        "请主动了解用户的职业背景、兴趣偏好和沟通风格，并记录到用户画像中。",
        "1.0.0",
        90
    ),

    /** 自学习：基于用户反馈或任务失败触发学习流程 */
    SELF_LEARNING(
        "builtin-self-learning",
        "自学习",
        "基于用户反馈或任务失败，触发学习流程改进技能和提示词",
        null,
        "[\"你做错了\",\"不对\",\"重新学习\",\"改进一下\",\"学习\"]",
        "分析本次任务的执行轨迹，识别改进点，更新相关技能和提示词。",
        "1.0.0",
        80
    ),

    /** 创建技能：引导用户定义新技能并持久化 */
    SKILL_CREATION(
        "builtin-skill-creation",
        "创建技能",
        "引导用户定义新技能（触发条件/指令/工具），生成 SkillDefinition 并持久化",
        null,
        "[\"创建技能\",\"新建技能\",\"教你\",\"添加技能\",\"定义技能\"]",
        "引导用户提供技能名称、触发条件、执行指令和所需工具，然后创建并保存新技能。",
        "1.0.0",
        70
    );

    public final String skillId;
    public final String name;
    public final String description;
    public final String agentId;       // null 表示 Assistant 直接处理
    public final String triggerIntent;
    public final String systemPrompt;
    public final String version;
    public final int priority;

    BuiltinSkills(String skillId, String name, String description, String agentId,
                  String triggerIntent, String systemPrompt, String version, int priority) {
        this.skillId = skillId;
        this.name = name;
        this.description = description;
        this.agentId = agentId;
        this.triggerIntent = triggerIntent;
        this.systemPrompt = systemPrompt;
        this.version = version;
        this.priority = priority;
    }
}
