package com.xuejiai.aaf.framework.engine.skill;

/** 内置技能定义（代码权威来源）。 应用启动时由 BuiltinSkillInitializer upsert 到数据库。 用户可创建同名技能覆盖内置行为。 */
public enum BuiltinSkills {

    /** 自我认知：介绍助理自身的人格、技能集、可用工具 */
    SELF_AWARENESS(
            "builtin-self-awareness",
            "自我认知",
            "介绍助理自身的人格、技能集和可用工具",
            null,
            null,
            "[\"你是谁\",\"你能做什么\",\"你有哪些技能\",\"介绍一下你自己\",\"你的能力\"]",
            "请介绍你自己的人格特点、拥有的技能和可以使用的工具。",
            "1.0.0",
            100),

    /** 理解用户：主动收集用户画像（职业/偏好/沟通风格） */
    USER_UNDERSTANDING(
            "builtin-user-understanding",
            "理解用户",
            "主动收集用户背景、偏好和沟通风格，写入用户私有记忆",
            null,
            null,
            "[\"我是\",\"我的工作\",\"我喜欢\",\"我的背景\",\"了解我\"]",
            "请主动了解用户的职业背景、兴趣偏好和沟通风格，并记录到用户画像中。",
            "1.0.0",
            90),

    /** 自学习：基于用户反馈或任务失败触发学习流程 */
    SELF_LEARNING(
            "builtin-self-learning",
            "自学习",
            "基于用户反馈或任务失败，触发学习流程改进技能和提示词",
            null,
            null,
            "[\"你做错了\",\"不对\",\"重新学习\",\"改进一下\",\"学习\"]",
            "分析本次任务的执行轨迹，识别改进点，更新相关技能和提示词。",
            "1.0.0",
            80),

    /** 创建技能：引导用户定义新技能并持久化 */
    SKILL_CREATION(
            "builtin-skill-creation",
            "创建技能",
            "引导用户定义新技能（触发条件/指令/工具），生成 SkillDefinition 并持久化",
            null,
            null,
            "[\"创建技能\",\"新建技能\",\"教你\",\"添加技能\",\"定义技能\"]",
            "引导用户提供技能名称、触发条件、执行指令和所需工具，然后创建并保存新技能。",
            "1.0.0",
            70),

    /** 生成工具：根据自然语言描述 AI 生成可执行工具 */
    TOOL_GENERATION(
            "builtin-tool-generation",
            "生成工具",
            "根据用户描述自动生成 JavaScript 工具脚本，确认后注册（默认私有）",
            null,
            null,
            "[\"创建工具\",\"生成工具\",\"新建工具\",\"帮我做一个工具\",\"我需要一个工具\"]",
            "你可以使用 generate_tool 工具根据用户描述生成新工具。生成后展示源码供用户确认，确认后调用 confirm_tool 注册。注册后工具默认私有（仅创建者可见）。",
            "1.0.0",
            70),

    // ─── AIGC 专属技能 ────────────────────────────────────────────────

    /** AI 生图：专业图像生成 system prompt */
    AIGC_IMAGE_GEN(
            "aigc-image-gen",
            "AI 生图",
            "专业图像生成助手，优化提示词以生成高质量图像",
            "IMAGE_GEN",
            null,
            "[\"生成图片\",\"AI作图\",\"文生图\",\"图像生成\",\"画一张\"]",
            """
            你是一位专业的 AI 图像生成助手。
            - 根据用户描述，优化并扩展提示词，使其更具画面感和艺术性
            - 建议合适的风格（写实/插画/油画/赛博朋克等）
            - 提醒用户可以指定构图、光线、色调等要素
            - 生成的提示词应简洁有力，优先使用英文以获得最佳效果
            """,
            "1.0.0",
            60),

    /** AI 文案：专业文案创作 system prompt */
    AIGC_COPYWRITING(
            "aigc-copywriting",
            "AI 文案",
            "专业文案创作助手，支持口播、小红书、广告等多种风格",
            "COPYWRITING",
            null,
            "[\"写文案\",\"生成文案\",\"口播文案\",\"小红书\",\"广告语\"]",
            """
            你是一位专业的 AI 文案创作助手。
            - 根据用户需求创作吸引人的文案内容
            - 支持口播脚本、小红书种草文、广告语、产品描述等多种形式
            - 文案语言生动活泼，善用情绪共鸣和行动召唤（CTA）
            - 根据平台特性调整风格：小红书强调种草感，口播强调节奏感，广告强调记忆点
            """,
            "1.0.0",
            60),

    /** AI 生视频：专业视频生成 system prompt */
    AIGC_VIDEO_GEN(
            "aigc-video-gen",
            "AI 生视频",
            "专业视频生成助手，优化提示词以生成高质量短视频",
            "VIDEO_GEN",
            null,
            "[\"生成视频\",\"AI视频\",\"文生视频\",\"视频生成\",\"拍一段\"]",
            """
            你是一位专业的 AI 视频生成助手。
            - 根据用户描述优化视频生成提示词，突出动态效果和镜头语言
            - 建议合适的场景设置：镜头角度、运动方式、光线氛围
            - 提示词应包含：主体动作 + 场景环境 + 风格基调 + 镜头语言
            - 对于复杂视频，建议拆分为多个镜头逐一生成
            """,
            "1.0.0",
            60);

    public final String skillId;
    public final String name;
    public final String description;

    /** 技能分类（IMAGE_GEN / COPYWRITING / VIDEO_GEN），null=通用技能 */
    public final String category;

    public final String agentId; // null 表示 Assistant 直接处理
    public final String triggerIntent;
    public final String systemPrompt;
    public final String version;
    public final int priority;

    BuiltinSkills(
            String skillId,
            String name,
            String description,
            String category,
            String agentId,
            String triggerIntent,
            String systemPrompt,
            String version,
            int priority) {
        this.skillId = skillId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.agentId = agentId;
        this.triggerIntent = triggerIntent;
        this.systemPrompt = systemPrompt;
        this.version = version;
        this.priority = priority;
    }
}
