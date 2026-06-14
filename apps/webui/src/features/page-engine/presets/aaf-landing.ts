/**
 * AAF 产品首页 PageDef 配置——配置驱动的 Landing Page
 * @author AaronZZH & Kiro
 *
 * 内容结构：navbar → hero → features → showcase → stats → pricing → faq → cta → footer
 */

import { $url } from "@/lib/utils"
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
        title: "智能体应用开发框架",
        subtitle: "一句话搭建企业级 AI 应用——多智能体 · 工作流 · 知识库 · 无代码，开箱即用。",
        buttons: [
          { label: "快速开始", href: "/dashboard", variant: "default" },
          {
            label: "GitHub",
            href: "https://github.com/aaron-zzh/agentic-app-framework",
            variant: "outline"
          }
        ],
        backgroundType: "particles"
      },
      style: { fullWidth: true, animation: "fadeIn", padding: "none" }
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

    // ─── 深色卡片展示 ──────────────────────────────────────────────────────────
    {
      id: "feature-cards",
      type: "feature-cards",
      props: {
        title: "开箱即用 安全可控",
        subtitle: "每一项能力都已内建，无需自行搭建底层样板代码",
        columns: 2,
        items: [
          {
            icon: "key-round",
            title: "API Keys",
            description:
              "Give every user secure, production-ready API keys without building any of the underlying boilerplate code or UI."
          },
          {
            icon: "users",
            title: "多智能体协作",
            description:
              "通过意图路由和 Skill 注册，让多个 Agent 协同工作，自动编排任务流水线，无需人工干预。"
          },
          {
            icon: "git-branch",
            title: "工作流引擎",
            description:
              "可视化 AI 编排流水线，支持 LLM 节点、知识库节点、条件分支，对标 Dify 工作流能力。"
          },
          {
            icon: "database",
            title: "知识库管理",
            description:
              "向量数据库 + Neo4j 图谱双引擎，支持语义检索与时序知识图谱，让 AI 拥有长期记忆。"
          }
        ]
      },
      style: { fullWidth: true, animation: "fadeIn", padding: "none" }
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
            image: $url.cdn("/assets/demos/1.webp")
          },
          {
            label: "对话式交互",
            description: "自然语言驱动 UI 生成，Agent 实时协作，Tool Call 可视化。",
            image: $url.cdn("/assets/demos/2.webp")
          },
          {
            label: "工作流编排",
            description: "拖拽式流程设计器，支持审批、AI 任务、条件分支、并行网关。",
            image: $url.cdn("/assets/demos/3.webp")
          }
        ]
      },
      style: { animation: "fadeIn", backgroundColor: "var(--color-muted)" }
    },

    // ─── 状态管理 + Three.js ────────────────────────────────────────────────
    {
      id: "zustand-three",
      type: "zustand-three",
      props: {},
      style: { fullWidth: true, animation: "fadeIn", padding: "none" }
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
            cta: { label: "开始使用", href: "/dashboard" }
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
          { label: "快速开始", href: "/dashboard", variant: "default" },
          { label: "查看文档", href: "/docs", variant: "outline" }
        ]
      },
      style: { animation: "scaleIn" }
    }
  ]
}
