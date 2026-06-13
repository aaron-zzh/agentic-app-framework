/**
 * FeatureCardsSection — 深色卡片网格展示（图标 + 标题 + 描述）
 * 源自 examples/style-showcase 的四卡片 + 深色背景，封装为配置驱动的首页展示 Section
 * @author AaronZZH & Kiro
 */

"use client"

import { motion } from "framer-motion"
import { icons } from "lucide-react"

import { cn } from "@/lib/utils/cn"

import type { SectionComponentProps } from "../types"

interface FeatureCardItem {
  /** Lucide 图标名（kebab-case，如 "key-round"） */
  icon?: string
  title: string
  description?: string
}

interface FeatureCardsProps {
  title?: string
  subtitle?: string
  items?: FeatureCardItem[]
  columns?: 2 | 3 | 4
}

/** 默认卡片内容（与 style-showcase 示例保持一致） */
const DEFAULT_ITEMS: FeatureCardItem[] = [
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
    description: "可视化 AI 编排流水线，支持 LLM 节点、知识库节点、条件分支，对标 Dify 工作流能力。"
  },
  {
    icon: "database",
    title: "知识库管理",
    description: "向量数据库 + Neo4j 图谱双引擎，支持语义检索与时序知识图谱，让 AI 拥有长期记忆。"
  }
]

/** 根据图标名获取 Lucide 图标组件 */
function LucideIcon({ name }: { name: string }) {
  const Icon = icons[name as keyof typeof icons]
  if (!Icon) return null
  return <Icon className="mb-4 size-6 text-cyan-400" />
}

/** 深色卡片网格展示 Section */
export function FeatureCardsSection({ data }: SectionComponentProps) {
  const { title, subtitle, items = DEFAULT_ITEMS, columns = 2 } = data as FeatureCardsProps

  const gridCols = cn(
    "grid gap-6",
    columns === 2 && "grid-cols-1 md:grid-cols-2",
    columns === 3 && "grid-cols-1 md:grid-cols-2 lg:grid-cols-3",
    columns === 4 && "grid-cols-1 md:grid-cols-2 lg:grid-cols-4"
  )

  return (
    <section className="w-full bg-gray-950 px-6 py-16 md:py-24">
      <div className="mx-auto max-w-7xl">
        {/* 标题区 */}
        {(title || subtitle) && (
          <div className="mb-12 text-center">
            {title && <h2 className="font-bold text-3xl text-white">{title}</h2>}
            {subtitle && <p className="mt-3 text-gray-400">{subtitle}</p>}
          </div>
        )}

        {/* 卡片网格 */}
        <div className={gridCols}>
          {(items as FeatureCardItem[]).map((item, i) => (
            <motion.div
              key={item.title}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.08, duration: 0.4 }}
              className="relative isolate flex flex-col overflow-hidden rounded-2xl bg-gray-900 p-6 shadow-[inset_0_1px,inset_0_0_0_1px] shadow-white/[0.025]"
            >
              {item.icon && <LucideIcon name={item.icon} />}
              <h3 className="font-medium text-sm text-white">{item.title}</h3>
              {item.description && (
                <p className="mt-2 max-w-sm text-pretty text-gray-400 text-sm/5">
                  {item.description}
                </p>
              )}
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}
