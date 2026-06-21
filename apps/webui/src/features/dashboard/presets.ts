/**
 * 仪表盘预设模板——运营/技术/财务三套预设布局
 * @author AaronZZH & Kiro
 */

import type { DashboardWidgetVO } from "@/lib/api/rest/dashboard/dashboard"

export interface DashboardPreset {
  key: string
  name: string
  description: string
  widgets: DashboardWidgetVO[]
  refreshInterval: number
  /** 仅管理员可使用（super_admin / admin / org_admin） */
  adminOnly?: boolean
}

/** 运营仪表盘预设 */
const operationsPreset: DashboardPreset = {
  key: "operations",
  name: "运营仪表盘",
  description: "DAU/MAU 趋势、用户漏斗、留存率分析",
  adminOnly: true,
  refreshInterval: 60,
  widgets: [
    {
      id: "ops-dau-trend",
      type: "echarts",
      title: "DAU 趋势",
      position: { x: 0, y: 0, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "dau",
        period: "day"
      }
    },
    {
      id: "ops-mau-trend",
      type: "echarts",
      title: "MAU 趋势",
      position: { x: 6, y: 0, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "bar",
        metric: "mau",
        period: "month"
      }
    },
    {
      id: "ops-funnel",
      type: "echarts",
      title: "用户行为漏斗",
      position: { x: 0, y: 4, w: 6, h: 4 },
      config: { type: "echarts", statsType: "funnel" }
    },
    {
      id: "ops-retention",
      type: "echarts",
      title: "用户留存率",
      position: { x: 6, y: 4, w: 6, h: 4 },
      config: { type: "echarts", statsType: "retention" }
    }
  ]
}

/** 技术仪表盘预设 */
const techPreset: DashboardPreset = {
  key: "tech",
  name: "技术仪表盘",
  description: "API 调用量、错误率、响应时间监控",
  adminOnly: true,
  refreshInterval: 30,
  widgets: [
    {
      id: "tech-api-calls",
      type: "echarts",
      title: "API 调用量",
      position: { x: 0, y: 0, w: 8, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "api_calls",
        period: "hour"
      }
    },
    {
      id: "tech-error-rate",
      type: "echarts",
      title: "错误率",
      position: { x: 8, y: 0, w: 4, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "bar",
        metric: "error_rate",
        period: "hour"
      }
    },
    {
      id: "tech-latency",
      type: "echarts",
      title: "平均响应时间",
      position: { x: 0, y: 4, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "avg_latency",
        period: "hour"
      }
    },
    {
      id: "tech-active-users",
      type: "counter",
      title: "在线用户",
      position: { x: 6, y: 4, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "user",
        aggregation: "count",
        icon: "users",
        color: "blue"
      }
    },
    {
      id: "tech-uptime",
      type: "progress",
      title: "系统可用率",
      position: { x: 9, y: 4, w: 3, h: 2 },
      config: { type: "progress", label: "可用率", current: 99.9, target: 100 }
    }
  ]
}

/** 财务仪表盘预设 */
const financePreset: DashboardPreset = {
  key: "finance",
  name: "财务仪表盘",
  description: "收入趋势、订阅转化、Token 消耗",
  adminOnly: true,
  refreshInterval: 300,
  widgets: [
    {
      id: "fin-revenue",
      type: "echarts",
      title: "收入趋势",
      position: { x: 0, y: 0, w: 8, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "bar",
        metric: "revenue",
        period: "day"
      }
    },
    {
      id: "fin-conversion",
      type: "echarts",
      title: "订阅转化漏斗",
      position: { x: 8, y: 0, w: 4, h: 4 },
      config: { type: "echarts", statsType: "funnel" }
    },
    {
      id: "fin-token-usage",
      type: "echarts",
      title: "Token 消耗趋势",
      position: { x: 0, y: 4, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "token_usage",
        period: "day"
      }
    },
    {
      id: "fin-arpu",
      type: "echarts",
      title: "ARPU 趋势",
      position: { x: 6, y: 4, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "arpu",
        period: "month"
      }
    }
  ]
}

/** 金融仪表盘预设 */
const bankingPreset: DashboardPreset = {
  key: "banking",
  name: "金融仪表盘",
  description: "账户余额、收支趋势、支出分类、近期交易",
  adminOnly: true,
  refreshInterval: 300,
  widgets: [
    {
      id: "bank-overview",
      type: "finance",
      title: "总览",
      position: { x: 0, y: 0, w: 8, h: 7 },
      config: { type: "finance", component: "overview" }
    },
    {
      id: "bank-current-balance",
      type: "finance",
      title: "当前余额",
      position: { x: 8, y: 0, w: 4, h: 4 },
      config: { type: "finance", component: "card-carousel" }
    },
    {
      id: "bank-balance-stats",
      type: "finance",
      title: "Balance statistics",
      position: { x: 0, y: 7, w: 8, h: 6 },
      config: { type: "finance", component: "multi-series-chart" }
    },
    {
      id: "bank-expenses",
      type: "finance",
      title: "Expenses categories",
      position: { x: 8, y: 4, w: 4, h: 6 },
      config: { type: "finance", component: "expenses-category" }
    },
    {
      id: "bank-transactions",
      type: "finance",
      title: "Recent transitions",
      position: { x: 0, y: 13, w: 8, h: 5 },
      config: { type: "finance", component: "transaction-list" }
    }
  ]
}

/** 个人工作台预设（普通用户默认） */
const personalPreset: DashboardPreset = {
  key: "personal",
  name: "个人工作台",
  description: "快捷入口、积分余额、AI 创作统计",
  refreshInterval: 300,
  widgets: [
    {
      id: "personal-shortcuts",
      type: "shortcut",
      title: "快捷入口",
      position: { x: 0, y: 0, w: 12, h: 3 },
      config: {
        type: "shortcut",
        items: [
          { label: "AI 创作", href: "/aigc", icon: "sparkles" },
          { label: "素材库", href: "/aigc/assets", icon: "image" },
          { label: "知识库", href: "/knowledge", icon: "database" },
          { label: "设置", href: "/settings", icon: "settings" }
        ]
      }
    },
    {
      id: "personal-credits",
      type: "counter",
      title: "积分余额",
      position: { x: 0, y: 3, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "@total_credit",
        aggregation: "count",
        icon: "credit-card",
        color: "yellow"
      }
    },
    {
      id: "personal-assets",
      type: "counter",
      title: "我的素材",
      position: { x: 3, y: 3, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "media_asset",
        aggregation: "count",
        icon: "image",
        color: "purple"
      }
    },
    {
      id: "personal-aigc-tasks",
      type: "counter",
      title: "生成任务",
      position: { x: 6, y: 3, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "aigc_task",
        aggregation: "count",
        icon: "wand-2",
        color: "blue"
      }
    },
    {
      id: "personal-knowledge",
      type: "counter",
      title: "知识库数量",
      position: { x: 9, y: 3, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "ai_knowledge_base",
        aggregation: "count",
        icon: "database",
        color: "green"
      }
    },
    {
      id: "personal-task-trend",
      type: "echarts",
      title: "生成任务趋势",
      position: { x: 0, y: 5, w: 8, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "bar",
        metric: "aigc_task",
        period: "day"
      }
    },
    {
      id: "personal-credit-trend",
      type: "echarts",
      title: "积分消耗趋势",
      position: { x: 8, y: 5, w: 4, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "credit_cost",
        period: "day"
      }
    }
  ]
}

/** 管理员运营仪表盘预设 */
const adminPreset: DashboardPreset = {
  key: "admin",
  name: "运营总览",
  description: "注册用户、付费会员、订单、积分等核心运营指标",
  adminOnly: true,
  refreshInterval: 300,
  widgets: [
    {
      id: "admin-user-count",
      type: "counter",
      title: "注册用户",
      position: { x: 0, y: 0, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "@user_count",
        aggregation: "count",
        icon: "users",
        color: "blue"
      }
    },
    {
      id: "admin-paid-member",
      type: "counter",
      title: "付费会员",
      position: { x: 3, y: 0, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "@paid_member",
        aggregation: "count",
        icon: "badge-check",
        color: "yellow"
      }
    },
    {
      id: "admin-order-count",
      type: "counter",
      title: "订单数",
      position: { x: 6, y: 0, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "@order_count",
        aggregation: "count",
        icon: "receipt",
        color: "green"
      }
    },
    {
      id: "admin-order-amount",
      type: "counter",
      title: "订单总额（分）",
      position: { x: 9, y: 0, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "@order_amount",
        aggregation: "sum",
        icon: "credit-card",
        color: "purple"
      }
    },
    {
      id: "admin-total-credit",
      type: "counter",
      title: "积分总量",
      position: { x: 0, y: 2, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "@total_credit",
        aggregation: "sum",
        icon: "credit-card",
        color: "orange"
      }
    },
    {
      id: "admin-spent-credit",
      type: "counter",
      title: "已消耗积分",
      position: { x: 3, y: 2, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "@spent_credit",
        aggregation: "sum",
        icon: "credit-card",
        color: "red"
      }
    },
    {
      id: "admin-aigc-task",
      type: "counter",
      title: "AIGC 任务数",
      position: { x: 6, y: 2, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "aigc_task",
        aggregation: "count",
        icon: "wand-2",
        color: "blue"
      }
    },
    {
      id: "admin-kb-count",
      type: "counter",
      title: "知识库数量",
      position: { x: 9, y: 2, w: 3, h: 2 },
      config: {
        type: "counter",
        entity: "ai_knowledge_base",
        aggregation: "count",
        icon: "database",
        color: "green"
      }
    },
    {
      id: "admin-dau-trend",
      type: "echarts",
      title: "DAU 趋势",
      position: { x: 0, y: 4, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "line",
        metric: "dau",
        period: "day"
      }
    },
    {
      id: "admin-revenue-trend",
      type: "echarts",
      title: "收入趋势",
      position: { x: 6, y: 4, w: 6, h: 4 },
      config: {
        type: "echarts",
        statsType: "trend",
        chartType: "bar",
        metric: "revenue",
        period: "day"
      }
    }
  ]
}

/** 所有预设模板 */
export const dashboardPresets: DashboardPreset[] = [
  personalPreset,
  adminPreset,
  operationsPreset,
  techPreset,
  financePreset,
  bankingPreset
]
