/**
 * AAF 产品首页 PageDef 配置——配置驱动的 Landing Page
 * @author AaronZZH & Kiro
 *
 * 内容结构：navbar → hero → features → showcase → stats → pricing → faq → cta → footer
 */

import type { PageDef } from "../types"

/** AAF 产品首页配置 */
export const aafLandingPageDef: PageDef = {
  slug: "home",
  title: "AAF — AI 原生多智能体应用开发框架",
  metadata: {
    title: "AAF — AI 原生多智能体应用开发框架",
    description:
      "生产级 AI 原生多智能体应用开发框架。多智能体协作 · 工作流引擎 · 知识库管理 · 配置驱动 · 无代码开发。",
    keywords: ["AI", "多智能体", "低代码", "工作流", "知识库", "Spring Boot", "Next.js"]
  },
  theme: {
    darkMode: "system"
  },
  sections: [
    // ─── Hero ────────────────────────────────────────────────────────────────
    {
      id: "hero",
      type: "hero",
      props: {
        title: "AI 原生多智能体应用开发框架",
        subtitle:
          "从对话到产品，一句话创建企业级应用。多智能体协作 · 配置驱动视图 · 工作流引擎 · 知识库管理 · 无代码开发。",
        buttons: [
          { label: "快速开始", href: "/docs/getting-started", variant: "default" },
          { label: "GitHub", href: "https://github.com/xuejiai/aaf", variant: "outline" }
        ],
        backgroundType: "gradient"
      },
      style: { fullWidth: true, animation: "fadeIn" }
    },

    // ─── 核心能力 ────────────────────────────────────────────────────────────
    {
      id: "features",
      type: "features",
      props: {
        title: "核心能力",
        subtitle: "AI 是架构的一等公民，不是附加物",
        columns: 3,
        items: [
          {
            icon: "users",
            title: "多智能体协作",
            description: "多 Agent 并行执行，各自独立 runtime，状态互相感知，人工审批无缝介入。"
          },
          {
            icon: "layout-grid",
            title: "配置驱动视图",
            description: "注册 EntityDef 即生成完整 CRUD 界面——列表、表单、看板、图表、透视。"
          },
          {
            icon: "brain",
            title: "AI 感知与辅助",
            description: "AI 全面了解页面上下文，主动提供字段补全、操作建议、错误修复。"
          },
          {
            icon: "git-branch",
            title: "工作流引擎",
            description: "可视化流程编排，审批节点 + AI 工作流 + 自动化规则，Flowable 驱动。"
          },
          {
            icon: "book-open",
            title: "知识库管理",
            description: "文档上传 → 向量化 → RAG 检索，支持多模态知识和实时更新。"
          },
          {
            icon: "wand-2",
            title: "无代码开发",
            description: "对话创建实体 → 系统自动建表 + API + UI，全程零代码、无需部署。"
          },
          {
            icon: "shield-check",
            title: "企业级安全",
            description: "RBAC + 行级数据权限 + 审计日志 + 多租户隔离，满足合规要求。"
          },
          {
            icon: "puzzle",
            title: "插件生态",
            description:
              "registerFieldType / registerViewType / registerSectionType，第三方可扩展一切。"
          }
        ]
      },
      style: { animation: "slideUp" }
    },

    // ─── 产品展示 ────────────────────────────────────────────────────────────
    {
      id: "showcase",
      type: "showcase",
      props: {
        title: "产品演示",
        tabs: [
          {
            label: "结构化视图",
            description: "配置驱动的列表、表单、看板视图，支持行内编辑、批量操作、多视图切换。",
            image: "/images/showcase-structured.png"
          },
          {
            label: "对话式交互",
            description: "自然语言驱动 UI 生成，Agent 实时协作，Tool Call 可视化。",
            image: "/images/showcase-chat.png"
          },
          {
            label: "工作流编排",
            description: "拖拽式流程设计器，支持审批、AI 任务、条件分支、并行网关。",
            image: "/images/showcase-workflow.png"
          }
        ]
      },
      style: { animation: "fadeIn", backgroundColor: "var(--color-muted)" }
    },

    // ─── 数据统计 ────────────────────────────────────────────────────────────
    {
      id: "stats",
      type: "stats",
      props: {
        items: [
          { value: "64", label: "预定义 Section 能力", suffix: "+" },
          { value: "5", label: "智能架构层", suffix: " 层" },
          { value: "100", label: "TypeScript 类型覆盖", suffix: "%" },
          { value: "0", label: "运行时 any", suffix: "" }
        ]
      },
      style: { animation: "slideUp" }
    },

    // ─── 定价方案 ────────────────────────────────────────────────────────────
    {
      id: "pricing",
      type: "pricing",
      props: {
        title: "定价方案",
        subtitle: "选择适合你的方案",
        plans: [
          {
            name: "社区版",
            price: "免费",
            description: "个人开发者 / 学习",
            features: [
              "完整开源代码",
              "社区支持",
              "基础 Agent 能力",
              "单租户部署",
              "5 个自定义实体"
            ],
            cta: { label: "开始使用", href: "/docs/getting-started" }
          },
          {
            name: "专业版",
            price: "¥299/月",
            description: "中小团队",
            highlighted: true,
            features: [
              "社区版全部功能",
              "优先技术支持",
              "多租户 + RBAC",
              "高级工作流",
              "无限自定义实体",
              "AI 感知增强",
              "数据导入导出"
            ],
            cta: { label: "免费试用", href: "/signup?plan=pro" }
          },
          {
            name: "企业版",
            price: "联系销售",
            description: "大型企业 / 定制需求",
            features: [
              "专业版全部功能",
              "专属技术顾问",
              "私有化部署",
              "SLA 保障",
              "定制开发",
              "安全审计报告",
              "培训服务"
            ],
            cta: { label: "联系我们", href: "/contact" }
          }
        ]
      },
      style: { animation: "slideUp" }
    },

    // ─── FAQ ─────────────────────────────────────────────────────────────────
    {
      id: "faq",
      type: "faq",
      props: {
        title: "常见问题",
        items: [
          {
            question: "AAF 和传统低代码平台有什么区别？",
            answer:
              "AAF 是 AI 原生架构——AI 是一等公民而非附加物。传统低代码平台通过拖拽生成代码，AAF 通过自然语言对话创建完整应用，配置驱动视图引擎自动生成 UI，同时保留 TypeScript 全链路类型安全和代码可控性。"
          },
          {
            question: "需要什么技术背景才能使用？",
            answer:
              "社区版面向开发者，需要 TypeScript/Java 基础。专业版和企业版的无代码能力让业务人员也能通过对话创建应用模块，无需编程经验。"
          },
          {
            question: "数据安全如何保障？",
            answer:
              "AAF 支持私有化部署，数据完全在你的服务器上。内置 RBAC + 行级数据权限 + 审计日志 + 多租户隔离，满足企业合规要求。AI 感知数据不离开前端或仅发送到用户授权的 Agent。"
          },
          {
            question: "可以和现有系统集成吗？",
            answer:
              "支持。AAF 提供 REST + GraphQL API、WebSocket、MCP 协议、Webhook 等多种集成方式。工作流引擎支持调用外部接口，插件系统可扩展任意能力。"
          },
          {
            question: "开源协议是什么？",
            answer: "AAF 核心框架采用 Apache 2.0 协议开源，企业版增值功能采用商业许可。"
          }
        ]
      },
      style: { animation: "fadeIn" }
    },

    // ─── CTA ─────────────────────────────────────────────────────────────────
    {
      id: "cta",
      type: "cta",
      props: {
        title: "准备好开始了吗？",
        description: "5 分钟内启动你的第一个 AI 原生应用",
        buttons: [
          { label: "快速开始", href: "/docs/getting-started", variant: "default" },
          { label: "查看文档", href: "/docs", variant: "outline" }
        ]
      },
      style: { animation: "scaleIn" }
    }
  ]
}
