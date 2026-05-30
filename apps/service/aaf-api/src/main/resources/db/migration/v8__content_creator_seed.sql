-- ============================================================
-- 内容创作助理：内置技能 + Agent + Role 种子数据
-- ============================================================

-- 内置技能：content-judge（爆款结构拆解器）
INSERT INTO ai_skill_definition (skill_id, name, description, trigger_intent, instructions, tools, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    'content-judge',
    '爆款结构拆解器',
    '分析爆款内容结构，判断是否值得复用。输入任意内容，输出核心观点、目标读者、展开路径、注意力钩子、情绪曲线、论证方式和可复用表达结构。',
    '["分析爆款","拆解结构","为什么火","分析内容","爆款分析","内容拆解"]',
    E'# 爆款结构拆解器 (Content-Judge)\n\n## 目标\n不是学写作，而是学「判断什么值得写」。\n\n## 规则\n- 不改写、不润色原内容\n- 不主观夸赞\n- 信息不足请标注「未知」\n- 判断基于结构与传播机制，而非个人喜好\n\n## 输出格式（严格按以下结构）\n\n1）核心观点（一句话）\n2）目标读者与使用场景\n3）内容展开路径（编号列表）\n4）注意力钩子（类型 + 原句）\n5）情绪变化曲线（开头 / 中段 / 结尾）\n6）论证方式（如：故事 / 对比 / 权威 / 反直觉）\n7）可复用表达结构（3-5 个模板）\n8）复用判断（是否值得复用 + 原因）',
    '["collect"]',
    10, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (skill_id) DO UPDATE SET
    instructions = EXCLUDED.instructions,
    skill_version = EXCLUDED.skill_version,
    update_time = CURRENT_TIMESTAMP;

-- 内置技能：content-clarify（写作前元思考澄清器）
INSERT INTO ai_skill_definition (skill_id, name, description, trigger_intent, instructions, tools, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    'content-clarify',
    '写作前元思考澄清器',
    '解决「我知道要写什么，但就是写不出来」。在写作前强制澄清 6 个关键决策变量。',
    '["不知道写什么","写作卡壳","逻辑混乱","想法很多","写不出来","澄清思路"]',
    E'# 写作前元思考澄清器 (Content-Clarify)\n\n## 目标\n在写作前强制澄清关键决策变量。\n\n## 引导用户回答以下 6 个问题\n\n1. 目标读者是谁？（具体画像，不是"所有人"）\n2. 发布平台是什么？（决定格式和语气）\n3. 读者此刻的真实痛点或欲望是什么？\n4. 这次内容的核心判断或结论是什么？（一句话）\n5. 内容将基于哪些经验/案例/证据？\n6. 整体表达风格偏向哪一种？（教学/故事/对话/清单/反直觉）\n\n## 规则\n- 逐个引导，不要一次性抛出所有问题\n- 用户回答模糊时追问细化\n- 6 个问题回答完毕后，输出一份「写作决策摘要」',
    NULL,
    10, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (skill_id) DO UPDATE SET
    instructions = EXCLUDED.instructions,
    skill_version = EXCLUDED.skill_version,
    update_time = CURRENT_TIMESTAMP;

-- 内置技能：content-architect（母内容结构构建器）
INSERT INTO ai_skill_definition (skill_id, name, description, trigger_intent, instructions, tools, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    'content-architect',
    '母内容结构构建器',
    '将已验证观点升级为可长期复用的核心内容结构。生成完整结构蓝图，包括钩子方案、正文结构、CTA 和裂变方向。',
    '["设计结构","写母内容","内容结构","构建文章","文章大纲","内容架构"]',
    E'# 母内容结构构建器 (Content-Architect)\n\n## 目标\n将已验证观点升级为「可长期复用的核心内容」。\n\n## 输出格式\n\n1）一句话承诺（读完能获得什么）\n2）开头钩子方案（3 个备选）\n3）正文结构\n   - 段落标题\n   - 段落目的\n   - 核心要点\n4）CTA 设计（软 CTA + 硬 CTA 各一）\n5）后续可裂变方向（5 个）\n\n## 规则\n- 基于用户提供的核心观点和素材\n- 结构必须可直接用于写作\n- 每个段落有明确目的，不允许「凑字数」段落',
    '["createDocument"]',
    10, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (skill_id) DO UPDATE SET
    instructions = EXCLUDED.instructions,
    skill_version = EXCLUDED.skill_version,
    update_time = CURRENT_TIMESTAMP;

-- 内置技能：content-build（内容裂变与复利引擎）
INSERT INTO ai_skill_definition (skill_id, name, description, trigger_intent, instructions, tools, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    'content-build',
    '内容裂变与复利引擎',
    '将一份母内容最大化利用，一次思考多次分发。生成短内容、强钩子、多平台版本、视频脚本和 CTA。',
    '["裂变内容","多平台分发","复用内容","改写成小红书","改写成公众号","一稿多用"]',
    E'# 内容裂变与复利引擎 (Content-Build)\n\n## 目标\n保持观点一致，生成多样表达，一次思考多平台使用。\n\n## 规则\n- 不新增核心观点，只拆观点\n- 每条内容只表达一个点\n- 表达方式必须不同\n\n## 输出格式\n\n1）短内容 × 10（100-200 字，适合社交媒体）\n2）强钩子 × 5（一句话，吸引点击）\n3）平台适配版本 × 3\n   - 公众号版（长图文，800-1500 字）\n   - 小红书版（图文笔记，300-500 字 + 配图建议）\n   - 抖音/视频号版（口播脚本，含前 3 秒钩子）\n4）CTA 备选 × 5',
    '["createDocument","publish"]',
    10, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (skill_id) DO UPDATE SET
    instructions = EXCLUDED.instructions,
    skill_version = EXCLUDED.skill_version,
    update_time = CURRENT_TIMESTAMP;

-- 内置技能：content-schedule（内容创作调度器）
INSERT INTO ai_skill_definition (skill_id, name, description, trigger_intent, instructions, tools, priority, built_in, skill_version, status, create_time, update_time)
VALUES (
    'content-schedule',
    '内容创作调度器',
    '协调调度内容创作全流程。按「拆解→澄清→构建→裂变」顺序引导用户，判断当前阶段并调用对应技能。',
    '["内容创作","写文章","创作内容","帮我写","内容永动机","系统化创作"]',
    E'# 内容创作调度器 (Content-Schedule)\n\n## 目标\n按照「拆解→想清楚→写一次→用到极致」的顺序调度创作流程。\n\n## 阶段判断标准\n\n**阶段 1 - 拆解**：用户提到分析爆款、学习结构 → 调用 content-judge\n**阶段 2 - 澄清**：用户不知道写什么、逻辑混乱 → 调用 content-clarify\n**阶段 3 - 构建**：用户有验证过的观点、要写正文 → 调用 content-architect\n**阶段 4 - 裂变**：用户已完成内容、要多平台分发 → 调用 content-build\n\n## 规则\n- 首次交互时评估用户处于哪个阶段\n- 如果用户直接说「帮我写一篇 XXX」，从阶段 2（澄清）开始\n- 如果用户提供了爆款内容要分析，从阶段 1 开始\n- 每个阶段完成后，主动引导进入下一阶段\n- 全程可调用文档工具保存中间产出',
    '["createDocument","updateDocument","publish","publishStatus","collect"]',
    20, true, '1.0', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (skill_id) DO UPDATE SET
    instructions = EXCLUDED.instructions,
    skill_version = EXCLUDED.skill_version,
    update_time = CURRENT_TIMESTAMP;

-- ============================================================
-- 内容创作助理 Agent
-- ============================================================

INSERT INTO ai_agent_definition (agent_id, name, description, system_prompt, model_id, capabilities, tools, max_iterations, timeout_seconds, status, create_time, update_time)
VALUES (
    'content-creator',
    '内容创作助理',
    '帮助用户完成内容创作全流程：爆款拆解→思路澄清→结构构建→内容裂变→多平台发布。',
    E'你是一位专业的内容创作助理，擅长帮助用户系统化地创作高质量内容。\n\n你的核心能力：\n1. 分析爆款内容的传播结构\n2. 引导用户澄清写作思路\n3. 构建可复用的内容结构\n4. 将一份内容裂变为多平台版本\n5. 协助发布到各平台（公众号、小红书、抖音、视频号）\n\n工作原则：\n- 按「拆解→澄清→构建→裂变→发布」的顺序引导\n- 每个阶段产出保存为文档，方便后续编辑\n- 用中文交流，语气专业但不生硬\n- 主动推进流程，不等用户追问',
    'deepseek:chat',
    'CHAT',
    '["createDocument","updateDocument","publish","publishStatus","collect"]',
    15, 180, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (agent_id) DO UPDATE SET
    system_prompt = EXCLUDED.system_prompt,
    tools = EXCLUDED.tools,
    update_time = CURRENT_TIMESTAMP;

-- 内容创作 Actor（人格）
INSERT INTO ai_actor (actor_id, name, persona, system_prompt, status, create_time, update_time)
VALUES (
    'content-creator-actor',
    '内容创作专家',
    '专业、有洞察力、善于引导。像一位资深内容策划师，既懂传播规律又懂用户心理。',
    '你是一位资深内容创作专家，帮助用户从 0 到 1 完成高质量内容创作和多平台分发。',
    'active',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (actor_id) DO NOTHING;

-- 内容创作 Role（能力集）
INSERT INTO ai_role (role_id, name, description, skill_ids, tool_whitelist, status, create_time, update_time)
VALUES (
    'content-creator-role',
    '内容创作能力集',
    '内容拆解、思路澄清、结构构建、内容裂变、多平台发布',
    '["content-schedule","content-judge","content-clarify","content-architect","content-build"]',
    '["createDocument","updateDocument","publish","publishStatus","collect"]',
    'active',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (role_id) DO UPDATE SET
    skill_ids = EXCLUDED.skill_ids,
    tool_whitelist = EXCLUDED.tool_whitelist,
    update_time = CURRENT_TIMESTAMP;

-- 内容创作 Assistant（Actor + Role 组合）
INSERT INTO ai_assistant (assistant_id, user_id, actor_id, role_id, memory_strategy, status, create_time, update_time)
VALUES (
    'content-creator-assistant',
    0,
    'content-creator-actor',
    'content-creator-role',
    'HYBRID',
    'active',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (assistant_id) DO NOTHING;
