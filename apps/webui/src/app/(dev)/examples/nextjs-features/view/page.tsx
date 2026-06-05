/**
 * 特性演示：next-view-transitions 使用说明页
 *
 * view-transition-name: container-move（CSS 属性）对整个容器指定过渡名称
 * 实现容器级别的"共享元素过渡"动画效果
 */
"use client"

import { useTransitionRouter } from "next-view-transitions"

function slideInOut() {
  document.documentElement.animate(
    [
      { opacity: 1, transform: "translate(0, 0)" },
      { opacity: 0, transform: "translate(-80px, 0)" }
    ],
    {
      duration: 350,
      easing: "ease",
      fill: "forwards",
      pseudoElement: "::view-transition-old(root)"
    }
  )
  document.documentElement.animate(
    [
      { opacity: 0, transform: "translate(80px, 0)" },
      { opacity: 1, transform: "translate(0, 0)" }
    ],
    {
      duration: 350,
      easing: "ease",
      fill: "forwards",
      pseudoElement: "::view-transition-new(root)"
    }
  )
}

export default function ViewTransitionsPage() {
  const router = useTransitionRouter()

  return (
    <div className="mx-auto max-w-2xl p-8">
      <div style={{ viewTransitionName: "container-move" }} className="space-y-6">
        <div>
          <p className="mb-2 text-sm">
            <span className="inline-block rounded bg-green-100 px-2 py-0.5 text-green-700 text-xs">
              🟢 浏览器支持 View Transitions
            </span>
          </p>
          <h1
            className="font-bold text-3xl text-slate-800"
            style={{ viewTransitionName: "page-title" }}
          >
            Next.js View Transitions
          </h1>
          <p className="mt-2 text-slate-600">
            通过{" "}
            <a
              href="https://developer.mozilla.org/en-US/docs/Web/API/View_Transitions_API"
              target="_blank"
              className="text-blue-600 underline"
              rel="noopener"
            >
              View Transitions API
            </a>{" "}
            在 Next.js App Router 中实现页面过渡动画。
          </p>
        </div>

        <section>
          <h2 className="mb-3 font-semibold text-slate-700 text-xl">安装</h2>
          <pre className="overflow-x-auto rounded-lg bg-slate-800 p-4 text-green-400 text-sm">
            <code>pnpm add next-view-transitions</code>
          </pre>
        </section>

        <section>
          <h2 className="mb-3 font-semibold text-slate-700 text-xl">基础用法</h2>
          <p className="mb-2 text-slate-600 text-sm">
            在 layout 中包裹 <code className="rounded bg-slate-100 px-1">ViewTransitions</code>：
          </p>
          <pre className="overflow-x-auto rounded-lg bg-slate-800 p-4 text-slate-100 text-sm">
            <code>{`import { ViewTransitions } from 'next-view-transitions'

export default function Layout({ children }) {
  return (
    <ViewTransitions>
      <html lang="en">
        <body>{children}</body>
      </html>
    </ViewTransitions>
  )
}`}</code>
          </pre>
        </section>

        <section>
          <h2 className="mb-3 font-semibold text-slate-700 text-xl">
            使用 Link 和 useTransitionRouter
          </h2>
          <pre className="overflow-x-auto rounded-lg bg-slate-800 p-4 text-slate-100 text-sm">
            <code>{`import { Link, useTransitionRouter } from 'next-view-transitions'

// Link：触发默认淡入淡出过渡
<Link href="/about">跳转</Link>

// useTransitionRouter：自定义动画
const router = useTransitionRouter()
router.push('/about', { onTransitionReady: myAnimation })`}</code>
          </pre>
        </section>

        <section>
          <h2 className="mb-3 font-semibold text-slate-700 text-xl">共享元素过渡（CSS）</h2>
          <pre className="overflow-x-auto rounded-lg bg-slate-800 p-4 text-slate-100 text-sm">
            <code>{`/* 为元素命名，跨页面自动过渡 */
<h1 style={{ viewTransitionName: 'hero-title' }}>标题</h1>
<div className={styles.container}>内容</div>

/* styles.module.css */
.container { view-transition-name: container-move; }`}</code>
          </pre>
        </section>

        <div className="flex gap-3 pt-2">
          <button
            onClick={() => router.push("/examples/nextjs-features")}
            className="rounded-lg bg-slate-200 px-4 py-2 text-slate-700 text-sm hover:bg-slate-300"
          >
            ← 返回首页
          </button>
          <button
            onClick={() =>
              router.push("/examples/nextjs-features", { onTransitionReady: slideInOut })
            }
            className="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-500"
          >
            自定义动画返回 →
          </button>
        </div>
      </div>
    </div>
  )
}
