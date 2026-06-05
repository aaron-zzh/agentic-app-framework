/**
 * 风格展示示例页——展示 AAF 卡片设计风格（亮暗主题均适用）
 */

"use client"

import { Moon, Sun } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"

/** API Keys 视觉 —— 服务器机架图 */
function ApiKeysVisual() {
  return (
    <div className="flex w-[200px] flex-col gap-[2px]">
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          className="flex h-8 items-center gap-2 rounded-md bg-gray-800 px-3 ring-1 ring-white/5"
        >
          <div className="h-2 w-2 rounded-full bg-gray-600" />
          <div className="h-1 flex-1 rounded-full bg-gray-700" />
          <div className={`h-1.5 w-1.5 rounded-full ${i === 1 ? "bg-cyan-400" : "bg-gray-600"}`} />
        </div>
      ))}
    </div>
  )
}

/** 多智能体视觉 —— 节点连接图 */
function AgentVisual() {
  return (
    <svg width="200" height="80" viewBox="0 0 200 80" fill="none">
      <line x1="40" y1="40" x2="100" y2="20" stroke="white" strokeOpacity="0.1" strokeWidth="1" />
      <line x1="40" y1="40" x2="100" y2="60" stroke="white" strokeOpacity="0.1" strokeWidth="1" />
      <line x1="100" y1="20" x2="160" y2="40" stroke="white" strokeOpacity="0.1" strokeWidth="1" />
      <line x1="100" y1="60" x2="160" y2="40" stroke="white" strokeOpacity="0.1" strokeWidth="1" />
      <circle cx="40" cy="40" r="10" fill="#1f2937" stroke="white" strokeOpacity="0.1" />
      <circle cx="100" cy="20" r="8" fill="#1f2937" stroke="#22d3ee" strokeOpacity="0.6" />
      <circle cx="100" cy="60" r="8" fill="#1f2937" stroke="white" strokeOpacity="0.1" />
      <circle cx="160" cy="40" r="10" fill="#1f2937" stroke="white" strokeOpacity="0.1" />
      <text x="40" y="44" textAnchor="middle" fill="white" fillOpacity="0.3" fontSize="7">
        A
      </text>
      <text x="100" y="23" textAnchor="middle" fill="#22d3ee" fontSize="7">
        B
      </text>
      <text x="100" y="63" textAnchor="middle" fill="white" fillOpacity="0.3" fontSize="7">
        C
      </text>
      <text x="160" y="44" textAnchor="middle" fill="white" fillOpacity="0.3" fontSize="7">
        D
      </text>
    </svg>
  )
}

/** 工作流视觉 —— 流程节点 */
function WorkflowVisual() {
  const nodes = [
    { label: "输入", accent: false },
    { label: "LLM", accent: true },
    { label: "知识库", accent: false },
    { label: "输出", accent: false }
  ]
  return (
    <div className="flex items-center gap-2">
      {nodes.map((node, i) => (
        <div key={node.label} className="flex items-center gap-2">
          <div
            className={`flex h-8 items-center justify-center rounded-md px-2 text-[10px] ring-1 ${
              node.accent
                ? "bg-cyan-950 text-cyan-400 ring-cyan-400/30"
                : "bg-gray-800 text-gray-400 ring-white/10"
            }`}
          >
            {node.label}
          </div>
          {i < nodes.length - 1 && (
            <svg width="12" height="8" viewBox="0 0 12 8" fill="none">
              <path d="M0 4h10M7 1l3 3-3 3" stroke="white" strokeOpacity="0.2" strokeWidth="1" />
            </svg>
          )}
        </div>
      ))}
    </div>
  )
}

/** 知识库视觉 —— 向量点阵 */
function KnowledgeVisual() {
  const litSet = new Set([2, 5, 11, 18])
  return (
    <svg width="200" height="70" viewBox="0 0 200 70" fill="none">
      {Array.from({ length: 24 }, (_, i) => (
        <circle
          key={i}
          cx={20 + (i % 8) * 22}
          cy={10 + Math.floor(i / 8) * 22}
          r="3"
          fill={litSet.has(i) ? "#22d3ee" : "white"}
          fillOpacity={litSet.has(i) ? 0.8 : 0.08}
        />
      ))}
    </svg>
  )
}

/** 卡片数据 */
const CARDS = [
  {
    title: "API Keys",
    description:
      "Give every user secure, production-ready API keys without building any of the underlying boilerplate code or UI.",
    Visual: ApiKeysVisual
  },
  {
    title: "多智能体协作",
    description:
      "通过意图路由和 Skill 注册，让多个 Agent 协同工作，自动编排任务流水线，无需人工干预。",
    Visual: AgentVisual
  },
  {
    title: "工作流引擎",
    description:
      "可视化 AI 编排流水线，支持 LLM 节点、知识库节点、条件分支，对标 Dify 工作流能力。",
    Visual: WorkflowVisual
  },
  {
    title: "知识库管理",
    description: "向量数据库 + Neo4j 图谱双引擎，支持语义检索与时序知识图谱，让 AI 拥有长期记忆。",
    Visual: KnowledgeVisual
  }
]

/** 展示卡片 */
function ShowcaseCard({
  title,
  description,
  Visual
}: {
  title: string
  description: string
  Visual: React.FC
}) {
  return (
    <div className="group relative isolate flex flex-col overflow-hidden rounded-2xl bg-gray-900 shadow-[inset_0_1px,inset_0_0_0_1px] shadow-white/[0.025]">
      <div className="relative z-20 flex-none px-6 pt-6 pb-6">
        <h3 className="font-medium text-sm text-white">{title}</h3>
        <p className="mt-2 max-w-sm text-pretty text-gray-400 text-sm/5">{description}</p>
      </div>
      <div className="pointer-events-none relative z-10 flex min-h-[10rem] flex-auto select-none items-center justify-center pb-6">
        <Visual />
      </div>
    </div>
  )
}

export default function StyleShowcasePage() {
  const [dark, setDark] = useState(true)

  return (
    <div className={dark ? "dark" : ""}>
      <div className="min-h-screen bg-gray-50 transition-colors duration-300 dark:bg-gray-950">
        {/* 顶栏 */}
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

        {/* 两列卡片网格 */}
        <div className="grid grid-cols-1 gap-6 px-8 pb-8 md:grid-cols-2">
          {CARDS.map((card) => (
            <ShowcaseCard
              key={card.title}
              title={card.title}
              description={card.description}
              Visual={card.Visual}
            />
          ))}
        </div>
      </div>
    </div>
  )
}
