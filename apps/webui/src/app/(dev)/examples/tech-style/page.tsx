/**
 * 科技风格展示页——细边框发光质感，强制暗色
 */

import type { Metadata } from "next"

export const metadata: Metadata = { title: "科技风格展示" }

export default function TechStylePage() {
  return (
    <div
      className="min-h-screen"
      style={{
        background: "radial-gradient(ellipse at 50% 0%, #0d1a3a 0%, #060b1a 60%, #030610 100%)"
      }}
    >
      {/* 网格底纹 */}
      <div
        className="pointer-events-none fixed inset-0"
        style={{
          backgroundImage:
            "linear-gradient(rgba(56,139,253,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(56,139,253,0.04) 1px, transparent 1px)",
          backgroundSize: "40px 40px"
        }}
      />

      <div className="relative mx-auto max-w-2xl px-6 py-16">
        {/* 标题 */}
        <div className="mb-12 text-center">
          <h1
            className="font-bold text-3xl tracking-wide"
            style={{
              background: "linear-gradient(135deg, #a8d4ff 0%, #c8b4ff 50%, #7eb8ff 100%)",
              WebkitBackgroundClip: "text",
              WebkitTextFillColor: "transparent"
            }}
          >
            AAF 核心能力
          </h1>
          <p className="mt-2 text-blue-300/50 text-sm">
            Agentic App Framework · 生产级 AI 原生框架
          </p>
        </div>

        {/* 功能卡片列表 */}
        <div className="space-y-4">
          {FEATURES.map((item) => (
            <FeatureRow key={item.title} {...item} />
          ))}
        </div>

        {/* 说明文字 */}
        <p className="mt-10 text-center text-blue-200/40 text-sm leading-relaxed">
          AI 是架构的一等公民，不是附加物。
          <br />
          规范驱动 · 多智能体 · 知识图谱 · 工作流引擎
        </p>

        {/* CTA 卡片 */}
        <CtaCard />

        {/* 底部 */}
        <p className="mt-8 text-center text-blue-300/30 text-xs">AAF v0.1.0 · Powered by Kiro</p>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* 数据                                                                  */
/* ------------------------------------------------------------------ */

const FEATURES = [
  {
    icon: (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
        className="size-5 text-blue-300"
        stroke="currentColor"
        strokeWidth={1.5}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09Z"
        />
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M18.259 8.715 18 9.75l-.259-1.035a3.375 3.375 0 0 0-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 0 0 2.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 0 0 2.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 0 0-2.456 2.456Z"
        />
      </svg>
    ),
    title: "多智能体协作",
    value: "Agent · Team · Assistant",
    accent: "cyan"
  },
  {
    icon: (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
        className="size-5 text-purple-300"
        stroke="currentColor"
        strokeWidth={1.5}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6A2.25 2.25 0 0 1 3.75 18v-2.25ZM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25A2.25 2.25 0 0 1 13.5 18v-2.25Z"
        />
      </svg>
    ),
    title: "工作流引擎",
    value: "LLM · 知识库 · 条件分支",
    accent: "purple"
  },
  {
    icon: (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
        className="size-5 text-blue-300"
        stroke="currentColor"
        strokeWidth={1.5}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M20.25 6.375c0 2.278-3.694 4.125-8.25 4.125S3.75 8.653 3.75 6.375m16.5 0c0-2.278-3.694-4.125-8.25-4.125S3.75 4.097 3.75 6.375m16.5 0v11.25c0 2.278-3.694 4.125-8.25 4.125s-8.25-1.847-8.25-4.125V6.375m16.5 2.5v4.625m-16.5-2.5v4.625"
        />
      </svg>
    ),
    title: "知识库管理",
    value: "PgVector · Neo4j · 语义检索",
    accent: "cyan"
  },
  {
    icon: (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
        className="size-5 text-violet-300"
        stroke="currentColor"
        strokeWidth={1.5}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M17.25 6.75 22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3-4.5 16.5"
        />
      </svg>
    ),
    title: "AI 自动开发",
    value: "代码生成 · 自我进化 · 规范驱动",
    accent: "violet"
  }
]

/* ------------------------------------------------------------------ */
/* 组件                                                                  */
/* ------------------------------------------------------------------ */

const ACCENT_MAP = {
  cyan: {
    ring: "rgba(34,211,238,0.25)",
    glow: "rgba(34,211,238,0.08)",
    iconBg: "rgba(34,211,238,0.06)",
    iconRing: "rgba(34,211,238,0.2)",
    value: "linear-gradient(135deg, #a8f0ff 0%, #67e8f9 100%)"
  },
  purple: {
    ring: "rgba(168,85,247,0.25)",
    glow: "rgba(168,85,247,0.08)",
    iconBg: "rgba(168,85,247,0.06)",
    iconRing: "rgba(168,85,247,0.2)",
    value: "linear-gradient(135deg, #ddd6fe 0%, #c084fc 100%)"
  },
  violet: {
    ring: "rgba(139,92,246,0.25)",
    glow: "rgba(139,92,246,0.08)",
    iconBg: "rgba(139,92,246,0.06)",
    iconRing: "rgba(139,92,246,0.2)",
    value: "linear-gradient(135deg, #e0d7ff 0%, #a78bfa 100%)"
  }
}

function FeatureRow({
  icon,
  title,
  value,
  accent
}: {
  icon: React.ReactNode
  title: string
  value: string
  accent: keyof typeof ACCENT_MAP
}) {
  const a = ACCENT_MAP[accent]
  return (
    <div
      className="flex items-center gap-4 rounded-xl px-5 py-4 transition-all duration-300"
      style={{
        background: `linear-gradient(135deg, rgba(255,255,255,0.03) 0%, rgba(255,255,255,0.01) 100%)`,
        border: "0.5px solid",
        borderColor: a.ring,
        boxShadow: `0 0 20px ${a.glow}, inset 0 1px 0 rgba(255,255,255,0.04)`
      }}
    >
      {/* 图标容器 */}
      <div
        className="flex size-10 shrink-0 items-center justify-center rounded-full"
        style={{
          background: a.iconBg,
          border: "0.5px solid",
          borderColor: a.iconRing,
          boxShadow: `0 0 12px ${a.glow}`
        }}
      >
        {icon}
      </div>

      {/* 文字 */}
      <div className="min-w-0 flex-1">
        <p className="font-semibold text-sm text-white/90">{title}</p>
        <p className="mt-0.5 truncate text-blue-300/40 text-xs">{value}</p>
      </div>

      {/* 右侧装饰线 */}
      <div
        className="hidden h-6 w-px sm:block"
        style={{ background: `linear-gradient(to bottom, transparent, ${a.ring}, transparent)` }}
      />
      <div
        className="hidden items-center gap-1 sm:flex"
        style={{
          background: a.value,
          WebkitBackgroundClip: "text",
          WebkitTextFillColor: "transparent"
        }}
      >
        <span className="whitespace-nowrap font-medium text-xs">查看详情</span>
        <svg
          viewBox="0 0 16 16"
          fill="currentColor"
          aria-hidden="true"
          className="size-3 opacity-60"
        >
          <path d="M6.22 3.22a.75.75 0 0 1 1.06 0l4.25 4.25a.75.75 0 0 1 0 1.06l-4.25 4.25a.75.75 0 0 1-1.06-1.06L9.94 8 6.22 4.28a.75.75 0 0 1 0-1.06Z" />
        </svg>
      </div>
    </div>
  )
}

function CtaCard() {
  return (
    <div
      className="mt-8 rounded-xl px-6 py-5 text-center"
      style={{
        background: "linear-gradient(135deg, rgba(99,102,241,0.08) 0%, rgba(168,85,247,0.06) 100%)",
        border: "0.5px solid rgba(139,92,246,0.3)",
        boxShadow: "0 0 30px rgba(139,92,246,0.1), inset 0 1px 0 rgba(255,255,255,0.05)"
      }}
    >
      {/* 装饰横线 */}
      <div className="mb-3 flex items-center justify-center gap-3">
        <div
          className="h-px flex-1"
          style={{ background: "linear-gradient(to right, transparent, rgba(139,92,246,0.4))" }}
        />
        <span className="text-violet-400/60 text-xs">·</span>
        <div
          className="h-px flex-1"
          style={{ background: "linear-gradient(to left, transparent, rgba(139,92,246,0.4))" }}
        />
      </div>

      <p
        className="font-bold text-lg"
        style={{
          background: "linear-gradient(135deg, #c4b5fd 0%, #93c5fd 100%)",
          WebkitBackgroundClip: "text",
          WebkitTextFillColor: "transparent"
        }}
      >
        开始构建你的 AI 应用
      </p>
      <p className="mt-1.5 text-blue-200/40 text-sm">
        从{" "}
        <span
          style={{
            background: "linear-gradient(135deg, #67e8f9, #a78bfa)",
            WebkitBackgroundClip: "text",
            WebkitTextFillColor: "transparent"
          }}
        >
          工具型
        </span>{" "}
        到{" "}
        <span
          style={{
            background: "linear-gradient(135deg, #67e8f9, #a78bfa)",
            WebkitBackgroundClip: "text",
            WebkitTextFillColor: "transparent"
          }}
        >
          系统集成型
        </span>
        ，AAF 全程覆盖
      </p>
    </div>
  )
}
