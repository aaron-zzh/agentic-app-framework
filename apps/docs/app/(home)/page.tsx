import Link from 'next/link';
import { Bot, Workflow, BookOpen, FileCode2, Wand2, LayoutDashboard, Plug, Brain, ShieldCheck } from 'lucide-react';

const features = [
  { icon: Bot, title: '多智能体协作', desc: '智能体系统、记忆系统、对话式交互' },
  { icon: Workflow, title: '工作流引擎', desc: '可视化工作流设计、DSL 定义' },
  { icon: BookOpen, title: '知识库管理', desc: '向量数据库、语义检索、知识图谱' },
  { icon: FileCode2, title: '规范驱动开发', desc: '先写规范，再写代码。让规范成为人类和 AI 的共同真理来源' },
  { icon: Wand2, title: 'AI 自动开发', desc: '代码生成、分析、优化、自我进化' },
  { icon: LayoutDashboard, title: '无代码开发', desc: '普通用户可视化搭建工作流、技能、知识库' },
  { icon: Plug, title: '外部生态整合', desc: '微信、钉钉、飞书等平台集成' },
  { icon: Brain, title: '元引擎架构', desc: '将意图转化为执行，将执行转化为知识，自我进化闭环' },
  { icon: ShieldCheck, title: '生产级可靠性', desc: '权限管理、开源授权控制、监控与审计全链路保障' },
];

const layers = [
  { name: 'Layer 5 · 对话与交互层', desc: '人机交互入口，意图表达与结果呈现，系统对外边界' },
  { name: 'Layer 4 · 服务层', desc: '面向用户的具体业务逻辑（Auto Dev、文档、用户、知识等）' },
  { name: 'Layer 3 · 智能层', desc: 'AI 推理与协作，五层智能架构（Core / Cognition / Agent / Assistant / Team）' },
  { name: 'Layer 2 · 引擎层', desc: '通用执行能力：调度、工作流、知识库、记忆、权限等专项引擎' },
  { name: 'Layer 1 · 基础设施层', desc: 'PostgreSQL、Redis、Neo4j、向量库、Agent Sandbox' },
];

export default function HomePage() {
  return (
    <>
      {/* Hero — 全屏，不受 max-w 限制 */}
      <section
        className="flex flex-col items-center justify-center text-center gap-6 min-h-[calc(100vh-4rem)] w-full relative"
        style={{
          backgroundImage: `url('https://picsum.photos/seed/${Math.floor(Math.random() * 100)}/1920/1080')`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
        }}
      >
        <div className="relative z-10 flex flex-col items-center gap-6 px-10 py-12 rounded-2xl bg-fd-background/60 backdrop-blur-md border border-fd-border shadow-xl">
          <h1 className="text-4xl font-bold">Agentic App Framework</h1>
          <p className="text-lg text-fd-muted-foreground max-w-2xl">
            AI 原生多智能体应用开发框架
          </p>
          <div className="flex flex-wrap gap-4 justify-center">
            <Link
              href="/docs/explanation/product-overview"
              className="px-6 py-3 rounded-lg bg-fd-primary text-fd-primary-foreground font-medium hover:opacity-90"
            >
              框架概述
            </Link>
            <Link
              href="/docs/design/architecture"
              className="px-6 py-3 rounded-lg border border-fd-border font-medium hover:bg-fd-accent"
            >
              架构设计
            </Link>
            <Link
              href="/docs/reference/Readme"
              className="px-6 py-3 rounded-lg border border-fd-border font-medium hover:bg-fd-accent"
            >
              开发规范
            </Link>
          </div>
        </div>
      </section>

      <main className="flex flex-col items-center px-4 py-16 max-w-5xl mx-auto w-full gap-16">
      <section className="w-full">
        <h2 className="text-2xl font-semibold mb-6 text-center">核心能力</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {features.map((f) => (
            <div key={f.title} className="rounded-lg border border-fd-border p-4">
              <div className="flex items-center gap-2 font-medium mb-1">
                <f.icon className="w-4 h-4 text-fd-primary shrink-0" />
                {f.title}
              </div>
              <div className="text-sm text-fd-muted-foreground">{f.desc}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Architecture layers */}
      <section className="w-full">
        <h2 className="text-2xl font-semibold mb-2 text-center">五层架构</h2>
        <p className="text-sm text-fd-muted-foreground text-center mb-6">
          上层可调用任意下层，禁止下层调用上层。
        </p>
        <div className="flex flex-col gap-2">
          {layers.map((l, i) => (
            <div
              key={l.name}
              className="rounded-lg border border-fd-border p-4 flex flex-col sm:flex-row sm:items-center gap-2"
              style={{ opacity: 1 - i * 0.08 }}
            >
              <span className="font-medium min-w-48 shrink-0">{l.name}</span>
              <span className="text-sm text-fd-muted-foreground">{l.desc}</span>
            </div>
          ))}
        </div>
      </section>

      {/* Design principles */}
      <section className="w-full text-center">
        <h2 className="text-2xl font-semibold mb-4">设计原则</h2>
        <p className="text-fd-muted-foreground">
          化繁为简 · DRY · 自动化 · 降低信息熵 · 价值驱动 · 最小可行实现 · 规范驱动 · AI 友好
        </p>
        <Link href="/docs/explanation/design-principles" className="text-sm text-fd-primary mt-2 inline-block hover:underline">
          查看设计原则文档 →
        </Link>
      </section>
    </main>
    </>
  );
}
