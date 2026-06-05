/**
 * 风格展示示例页——展示 AAF 卡片设计风格（亮暗主题均适用）
 */

"use client"

import type { LucideIcon } from "lucide-react"
import { Database, GitBranch, KeyRound, Moon, Sun, Users } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"

const CARDS: { title: string; description: string; Icon: LucideIcon }[] = [
  {
    title: "API Keys",
    description:
      "Give every user secure, production-ready API keys without building any of the underlying boilerplate code or UI.",
    Icon: KeyRound
  },
  {
    title: "多智能体协作",
    description:
      "通过意图路由和 Skill 注册，让多个 Agent 协同工作，自动编排任务流水线，无需人工干预。",
    Icon: Users
  },
  {
    title: "工作流引擎",
    description:
      "可视化 AI 编排流水线，支持 LLM 节点、知识库节点、条件分支，对标 Dify 工作流能力。",
    Icon: GitBranch
  },
  {
    title: "知识库管理",
    description: "向量数据库 + Neo4j 图谱双引擎，支持语义检索与时序知识图谱，让 AI 拥有长期记忆。",
    Icon: Database
  }
]

function ShowcaseCard({
  title,
  description,
  Icon
}: {
  title: string
  description: string
  Icon: LucideIcon
}) {
  return (
    <div className="relative isolate flex flex-col overflow-hidden rounded-2xl bg-gray-900 p-6 shadow-[inset_0_1px,inset_0_0_0_1px] shadow-white/[0.025]">
      <Icon className="mb-4 size-6 text-cyan-400" />
      <h3 className="font-medium text-sm text-white">{title}</h3>
      <p className="mt-2 max-w-sm text-pretty text-gray-400 text-sm/5">{description}</p>
    </div>
  )
}

export default function StyleShowcasePage() {
  const [dark, setDark] = useState(true)

  return (
    <div className={dark ? "dark" : ""}>
      <div className="min-h-screen bg-gray-50 transition-colors duration-300 dark:bg-gray-950">
        <div className="flex items-center justify-between px-8 py-6">
          <div>
            <h1 className="font-semibold text-2xl text-gray-900 dark:text-white">风格展示</h1>
            <p className="mt-1 text-gray-500 text-sm dark:text-gray-400">AAF 卡片设计规范示例</p>
          </div>
          <Button
            variant="outline"
            size="icon"
            onClick={() => setDark(!dark)}
            className="rounded-full border-gray-200 dark:border-gray-800"
          >
            {dark ? <Sun className="size-4" /> : <Moon className="size-4" />}
          </Button>
        </div>

        <div className="grid grid-cols-1 gap-6 px-8 pb-8 md:grid-cols-2">
          {CARDS.map((card) => (
            <ShowcaseCard key={card.title} {...card} />
          ))}
        </div>
      </div>
    </div>
  )
}
