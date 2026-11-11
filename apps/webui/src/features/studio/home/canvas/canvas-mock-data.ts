/**
 * AAF-125 示例数据，接口就绪后删除。
 *
 * - 协作关系：没有默认助理或没有绑定角色时展示的示例拓扑，与真实拓扑同形状（CollaborationTopology）。
 * - 运行实况：3 个示例任务的执行流程，含一个待审批步骤；将来由 AAF-115 的任务与执行树接口替换。
 * - 工作流：1 个示例 AI 工作流及其运行；项目任务：没有 AIGC 执行记录时的示例项目与执行。
 *
 * @example
 * const graph = buildCollaborationGraph(MOCK_COLLABORATION_TOPOLOGY, {
 *   expanded: new Set(),
 *   collapsedCategories: new Set()
 * })
 * const task = MOCK_RUN_TASKS[0]
 * @author AaronZZH & Kiro
 */

import type { CollaborationTopology, ProjectRunItem, ProjectRunProject, RunTaskDef } from "./types"

/**
 * 示例协作拓扑：1 个助理、9 个角色，分属"通用""内容创作""智慧农业"三个分类。
 * 智慧农业 4 个角色与种子数据（system.role.agri-*）一致：技能声明所需工具，角色只直连 context.load。
 */
export const MOCK_COLLABORATION_TOPOLOGY: CollaborationTopology = {
  assistantName: "平台向导",
  categories: [
    { id: "mock-cat-general", name: "通用" },
    { id: "mock-cat-content", name: "内容创作" },
    { id: "mock-cat-agri", name: "智慧农业" }
  ],
  roles: [
    {
      id: "mock-role-ops",
      name: "运营助理",
      visibility: "SYSTEM",
      enabled: true,
      defaultRole: true,
      skills: [
        { key: "mock.weekly-report", activationMode: "ALWAYS" },
        { key: "mock.summary", activationMode: "ON_DEMAND" },
        { key: "builtin-step-planning", activationMode: "ALWAYS" }
      ],
      tools: ["webSearch", "chart.render", "context.load"],
      knowledgeBaseIds: ["mock-kb-brand", "mock-kb-ads"],
      categoryId: "mock-cat-general"
    },
    {
      id: "mock-role-xhs",
      name: "小红书写手",
      visibility: "PUBLIC",
      enabled: true,
      defaultRole: false,
      skills: [{ key: "mock.xhs-copy", activationMode: "ALWAYS" }],
      tools: ["image.generate", "webSearch"],
      knowledgeBaseIds: ["mock-kb-brand", "mock-kb-product"],
      categoryId: "mock-cat-content"
    },
    {
      id: "mock-role-storyboard",
      name: "分镜师",
      visibility: "PRIVATE",
      enabled: true,
      defaultRole: false,
      skills: [
        { key: "mock.storyboard", activationMode: "ALWAYS" },
        { key: "mock.xhs-copy", activationMode: "ON_DEMAND" }
      ],
      tools: ["image.generate", "video.generate"],
      knowledgeBaseIds: ["mock-kb-product"],
      categoryId: "mock-cat-content"
    },
    {
      id: "mock-role-archivist",
      name: "资料整理员",
      visibility: "PRIVATE",
      enabled: false,
      defaultRole: false,
      skills: [{ key: "mock.summary", activationMode: "ALWAYS" }],
      tools: ["context.load"],
      knowledgeBaseIds: ["mock-kb-brand", "mock-kb-product"],
      categoryId: "mock-cat-general"
    },
    {
      id: "mock-role-growth",
      name: "增长分析",
      visibility: "SYSTEM",
      enabled: true,
      defaultRole: false,
      skills: [{ key: "mock.weekly-report", activationMode: "ON_DEMAND" }],
      tools: ["chart.render", "context.load"],
      knowledgeBaseIds: ["mock-kb-ads"],
      categoryId: "mock-cat-general"
    },
    {
      id: "mock-role-agri-advisor",
      name: "农技专家",
      visibility: "SYSTEM",
      enabled: true,
      defaultRole: false,
      skills: [{ key: "system.skill.agri-advisor", activationMode: "ALWAYS" }],
      tools: ["context.load", "webSearch", "queryWeather"],
      knowledgeBaseIds: ["mock-kb-agri-tech"],
      categoryId: "mock-cat-agri"
    },
    {
      id: "mock-role-agri-data",
      name: "农场数据分析师",
      visibility: "SYSTEM",
      enabled: true,
      defaultRole: false,
      skills: [{ key: "system.skill.agri-data-analysis", activationMode: "ALWAYS" }],
      tools: ["context.load", "agriSensorTrend", "agriSensorAlerts", "agriSalesSummary"],
      knowledgeBaseIds: [],
      categoryId: "mock-cat-agri"
    },
    {
      id: "mock-role-agri-marketing",
      name: "农产品营销助手",
      visibility: "SYSTEM",
      enabled: true,
      defaultRole: false,
      skills: [{ key: "system.skill.agri-marketing", activationMode: "ALWAYS" }],
      tools: ["context.load", "content.draft.upsert", "agriSalesSummary", "agriBookableProducts"],
      knowledgeBaseIds: ["mock-kb-agri-farm"],
      categoryId: "mock-cat-agri"
    },
    {
      id: "mock-role-agri-booking",
      name: "农场预约助手",
      visibility: "SYSTEM",
      enabled: true,
      defaultRole: false,
      skills: [{ key: "system.skill.agri-booking", activationMode: "ALWAYS" }],
      tools: ["context.load", "agriBookableProducts", "agriDraftBooking", "agriConfirmBooking"],
      knowledgeBaseIds: ["mock-kb-agri-farm"],
      categoryId: "mock-cat-agri"
    }
  ],
  skillNames: {
    "mock.weekly-report": "数据周报",
    "mock.summary": "长文摘要",
    "mock.xhs-copy": "小红书文案",
    "mock.storyboard": "分镜拆解",
    "builtin-step-planning": "步骤规划",
    "system.skill.agri-advisor": "农技专家",
    "system.skill.agri-data-analysis": "农场数据分析",
    "system.skill.agri-marketing": "农产品营销",
    "system.skill.agri-booking": "农场预约"
  },
  builtInSkillKeys: [
    "mock.weekly-report",
    "mock.summary",
    "builtin-step-planning",
    "system.skill.agri-advisor",
    "system.skill.agri-data-analysis",
    "system.skill.agri-marketing",
    "system.skill.agri-booking"
  ],
  skillTools: {
    "mock.weekly-report": ["chart.render"],
    "mock.xhs-copy": ["image.generate"],
    "mock.storyboard": ["image.generate", "video.generate"],
    "builtin-step-planning": ["plan_steps", "report_step"],
    "system.skill.agri-advisor": ["webSearch", "queryWeather"],
    "system.skill.agri-data-analysis": ["agriSensorTrend", "agriSensorAlerts", "agriSalesSummary"],
    "system.skill.agri-marketing": [
      "agriSalesSummary",
      "agriBookableProducts",
      "content.draft.upsert"
    ],
    "system.skill.agri-booking": ["agriBookableProducts", "agriDraftBooking", "agriConfirmBooking"]
  },
  knowledgeNames: {
    "mock-kb-brand": "品牌手册",
    "mock-kb-product": "产品资料",
    "mock-kb-ads": "投放数据",
    "mock-kb-agri-tech": "农技库",
    "mock-kb-agri-farm": "农场经营资料"
  }
}

/** 3 个示例任务，每个 8～12 个步骤，各含一个单独成阶段的审批步骤 */
export const MOCK_RUN_TASKS: readonly RunTaskDef[] = [
  {
    id: "launch-kit",
    name: "新品上市内容包",
    initialStage: 3,
    steps: [
      {
        id: "trigger",
        title: "用户发起任务",
        actor: "Studio 首页",
        icon: "trigger",
        stage: 0,
        durationSec: 1
      },
      {
        id: "coordinator",
        title: "协调者拆解任务",
        actor: "平台向导 · 运营助理",
        icon: "coordinator",
        stage: 1,
        durationSec: 6
      },
      {
        id: "plan",
        title: "制定执行计划",
        actor: "规划器",
        icon: "plan",
        stage: 2,
        durationSec: 4
      },
      {
        id: "memory",
        title: "读取历史偏好",
        actor: "长期记忆",
        icon: "memory",
        stage: 2,
        durationSec: 2
      },
      {
        id: "retrieve",
        title: "检索品牌资料",
        actor: "品牌手册",
        icon: "retrieve",
        stage: 2,
        durationSec: 5
      },
      {
        id: "copy",
        title: "撰写种草文案",
        actor: "小红书写手",
        icon: "write",
        stage: 3,
        durationSec: 28
      },
      {
        id: "visual",
        title: "生成主视觉",
        actor: "image.generate",
        icon: "image",
        stage: 3,
        durationSec: 42
      },
      {
        id: "video",
        title: "剪辑 15 秒短片",
        actor: "分镜师 · video.generate",
        icon: "video",
        stage: 4,
        durationSec: 96
      },
      {
        id: "approval",
        title: "人工审批",
        actor: "等待你确认发布",
        icon: "approval",
        stage: 5,
        durationSec: 0,
        approval: true
      },
      {
        id: "deliver",
        title: "交付到项目",
        actor: "我的项目",
        icon: "deliver",
        stage: 6,
        durationSec: 3
      }
    ],
    edges: [
      { from: "trigger", to: "coordinator", items: 1 },
      { from: "coordinator", to: "plan" },
      { from: "coordinator", to: "memory" },
      { from: "coordinator", to: "retrieve" },
      { from: "plan", to: "copy" },
      { from: "memory", to: "copy", items: 6 },
      { from: "retrieve", to: "copy", items: 22 },
      { from: "plan", to: "visual" },
      { from: "retrieve", to: "visual", items: 8 },
      { from: "copy", to: "video", items: 3 },
      { from: "visual", to: "video", items: 4 },
      { from: "video", to: "approval", items: 1 },
      { from: "approval", to: "deliver" }
    ]
  },
  {
    id: "weekly-report",
    name: "运营周报自动汇总",
    initialStage: 5,
    steps: [
      {
        id: "trigger",
        title: "每周一 09:00 定时触发",
        actor: "定时任务",
        icon: "trigger",
        stage: 0,
        durationSec: 1
      },
      {
        id: "coordinator",
        title: "协调者拆解任务",
        actor: "平台向导 · 增长分析",
        icon: "coordinator",
        stage: 1,
        durationSec: 5
      },
      {
        id: "ads",
        title: "拉取投放数据",
        actor: "投放数据",
        icon: "retrieve",
        stage: 2,
        durationSec: 8
      },
      {
        id: "news",
        title: "联网检索行业动态",
        actor: "web.search",
        icon: "search",
        stage: 2,
        durationSec: 12
      },
      {
        id: "memory",
        title: "读取上周结论",
        actor: "长期记忆",
        icon: "memory",
        stage: 2,
        durationSec: 2
      },
      {
        id: "chart",
        title: "生成数据图表",
        actor: "chart.render",
        icon: "tool",
        stage: 3,
        durationSec: 9
      },
      {
        id: "draft",
        title: "撰写周报正文",
        actor: "运营助理",
        icon: "write",
        stage: 3,
        durationSec: 34
      },
      {
        id: "review",
        title: "自检数据口径",
        actor: "运营助理",
        icon: "review",
        stage: 4,
        durationSec: 7
      },
      {
        id: "approval",
        title: "人工审批",
        actor: "等待你确认发送",
        icon: "approval",
        stage: 5,
        durationSec: 0,
        approval: true
      },
      {
        id: "deliver",
        title: "发送到运营群",
        actor: "消息通知",
        icon: "deliver",
        stage: 6,
        durationSec: 2
      }
    ],
    edges: [
      { from: "trigger", to: "coordinator", items: 1 },
      { from: "coordinator", to: "ads" },
      { from: "coordinator", to: "news" },
      { from: "coordinator", to: "memory" },
      { from: "ads", to: "chart", items: 1240 },
      { from: "ads", to: "draft", items: 36 },
      { from: "news", to: "draft", items: 18 },
      { from: "memory", to: "draft", items: 4 },
      { from: "chart", to: "review", items: 6 },
      { from: "draft", to: "review", items: 1 },
      { from: "review", to: "approval" },
      { from: "approval", to: "deliver" }
    ]
  },
  {
    id: "sentiment-watch",
    name: "竞品舆情监测",
    initialStage: 0,
    steps: [
      {
        id: "trigger",
        title: "监测关键词命中",
        actor: "舆情订阅",
        icon: "trigger",
        stage: 0,
        durationSec: 1
      },
      {
        id: "coordinator",
        title: "协调者拆解任务",
        actor: "平台向导 · 增长分析",
        icon: "coordinator",
        stage: 1,
        durationSec: 4
      },
      {
        id: "crawl",
        title: "抓取社媒内容",
        actor: "web.search",
        icon: "search",
        stage: 2,
        durationSec: 15
      },
      {
        id: "compare",
        title: "比对竞品资料",
        actor: "产品资料",
        icon: "retrieve",
        stage: 2,
        durationSec: 6
      },
      {
        id: "sentiment",
        title: "情感倾向分析",
        actor: "context.load",
        icon: "tool",
        stage: 3,
        durationSec: 11
      },
      {
        id: "summary",
        title: "提炼舆情要点",
        actor: "资料整理员",
        icon: "write",
        stage: 3,
        durationSec: 19
      },
      {
        id: "approval",
        title: "人工审批",
        actor: "确认是否发出预警",
        icon: "approval",
        stage: 4,
        durationSec: 0,
        approval: true
      },
      {
        id: "deliver",
        title: "推送预警",
        actor: "消息通知",
        icon: "deliver",
        stage: 5,
        durationSec: 2
      }
    ],
    edges: [
      { from: "trigger", to: "coordinator", items: 1 },
      { from: "coordinator", to: "crawl" },
      { from: "coordinator", to: "compare" },
      { from: "crawl", to: "sentiment", items: 318 },
      { from: "crawl", to: "summary", items: 42 },
      { from: "compare", to: "summary", items: 12 },
      { from: "sentiment", to: "approval", items: 3 },
      { from: "summary", to: "approval", items: 1 },
      { from: "approval", to: "deliver" }
    ]
  }
]

/**
 * 工作流标签页展示的示例 AI 工作流（对应 ai_flow_definition 的节点图），含一次示例运行。
 * 将来由 /api/ai/workflows 的定义与按工作流查询的运行实例接口替换。
 */
export const MOCK_WORKFLOW_RUNS: readonly RunTaskDef[] = [
  {
    id: "mock-workflow-support",
    name: "客户咨询自动答复",
    initialStage: 1,
    steps: [
      {
        id: "start",
        title: "开始",
        actor: "Webhook 触发",
        icon: "trigger",
        stage: 0,
        durationSec: 1
      },
      {
        id: "intent",
        title: "意图识别",
        actor: "LLM 节点",
        icon: "plan",
        stage: 1,
        durationSec: 4
      },
      {
        id: "knowledge",
        title: "知识库检索",
        actor: "知识库节点 · 产品手册",
        icon: "retrieve",
        stage: 2,
        durationSec: 3
      },
      {
        id: "history",
        title: "历史会话",
        actor: "记忆节点",
        icon: "memory",
        stage: 2,
        durationSec: 2
      },
      {
        id: "order",
        title: "订单查询",
        actor: "工具节点 · 订单系统",
        icon: "tool",
        stage: 2,
        durationSec: 2
      },
      {
        id: "draft",
        title: "生成答复",
        actor: "Agent 节点 · 客服专员",
        icon: "coordinator",
        stage: 3,
        durationSec: 9
      },
      {
        id: "review",
        title: "敏感词与合规检查",
        actor: "LLM 节点",
        icon: "review",
        stage: 4,
        durationSec: 3
      },
      {
        id: "approval",
        title: "人工确认",
        actor: "客服主管",
        icon: "approval",
        stage: 5,
        durationSec: 0,
        approval: true
      },
      {
        id: "send",
        title: "发送答复",
        actor: "渠道节点 · 企业微信",
        icon: "deliver",
        stage: 6,
        durationSec: 1
      }
    ],
    edges: [
      { from: "start", to: "intent", items: 1 },
      { from: "intent", to: "knowledge", items: 1 },
      { from: "intent", to: "history", items: 1 },
      { from: "intent", to: "order", items: 1 },
      { from: "knowledge", to: "draft", items: 6 },
      { from: "history", to: "draft", items: 12 },
      { from: "order", to: "draft", items: 2 },
      { from: "draft", to: "review", items: 1 },
      { from: "review", to: "approval", items: 1 },
      { from: "approval", to: "send" }
    ]
  }
]

/** 相对模块加载时刻的示例时间 */
function minutesAgo(minutes: number): string {
  return new Date(Date.now() - minutes * 60_000).toISOString()
}

/** 项目任务标签页没有真实执行记录时的示例项目 */
export const MOCK_PROJECTS: readonly ProjectRunProject[] = [
  { id: -1, name: "春季新品短视频" },
  { id: -2, name: "品牌 IP 形象设计" },
  { id: -3, name: "门店活动海报" }
]

/** 示例执行记录：根记录（parentId 为空）与其子记录 */
export const MOCK_PROJECT_RUNS: readonly ProjectRunItem[] = [
  {
    id: -101,
    projectId: -1,
    parentId: null,
    title: "生成分镜脚本",
    targetType: "AGENT",
    roleProfileCode: "分镜师",
    status: "RUNNING",
    startTime: minutesAgo(3),
    endTime: null
  },
  {
    id: -111,
    projectId: -1,
    parentId: -101,
    title: "拆解卖点",
    targetType: "AGENT",
    roleProfileCode: "文案策划",
    status: "SUCCEEDED",
    startTime: minutesAgo(3),
    endTime: minutesAgo(2)
  },
  {
    id: -112,
    projectId: -1,
    parentId: -101,
    title: "检索品牌素材",
    targetType: "TOOL",
    roleProfileCode: null,
    status: "SUCCEEDED",
    startTime: minutesAgo(3),
    endTime: minutesAgo(2)
  },
  {
    id: -113,
    projectId: -1,
    parentId: -111,
    title: "撰写分镜",
    targetType: "AGENT",
    roleProfileCode: "分镜师",
    status: "RUNNING",
    startTime: minutesAgo(1),
    endTime: null
  },
  {
    id: -114,
    projectId: -1,
    parentId: -113,
    title: "生成关键帧",
    targetType: "WORKFLOW",
    roleProfileCode: null,
    status: "PENDING",
    startTime: null,
    endTime: null
  },
  {
    id: -102,
    projectId: -1,
    parentId: null,
    title: "生成封面图",
    targetType: "TOOL",
    roleProfileCode: null,
    status: "SUCCEEDED",
    startTime: minutesAgo(40),
    endTime: minutesAgo(38)
  },
  {
    id: -201,
    projectId: -2,
    parentId: null,
    title: "IP 形象三视图",
    targetType: "WORKFLOW",
    roleProfileCode: null,
    status: "FAILED",
    startTime: minutesAgo(15),
    endTime: minutesAgo(12)
  },
  {
    id: -211,
    projectId: -2,
    parentId: -201,
    title: "正面形象",
    targetType: "TOOL",
    roleProfileCode: null,
    status: "SUCCEEDED",
    startTime: minutesAgo(15),
    endTime: minutesAgo(14)
  },
  {
    id: -212,
    projectId: -2,
    parentId: -201,
    title: "侧面形象",
    targetType: "TOOL",
    roleProfileCode: null,
    status: "FAILED",
    startTime: minutesAgo(14),
    endTime: minutesAgo(12)
  },
  {
    id: -301,
    projectId: -3,
    parentId: null,
    title: "活动海报三版",
    targetType: "AGENT",
    roleProfileCode: "平面设计师",
    status: "PENDING",
    startTime: null,
    endTime: null
  }
]
