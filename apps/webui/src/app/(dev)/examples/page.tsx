import Link from "next/link"

const EXAMPLES = [
  {
    category: "Three.js",
    items: [
      { title: "基础 Box + Birds", href: "/examples/threejs", desc: "R3F 基础几何体与粒子鸟群" },
      { title: "Demo 场景", href: "/examples/threejs/demo", desc: "Logo / Dog / Duck 多视口" },
      { title: "MeshLine", href: "/examples/threejs/meshline", desc: "MeshLine 描边效果" },
      { title: "视频纹理", href: "/examples/threejs/video", desc: "VideoTexture + Bloom 后处理" },
      {
        title: "粒子对比",
        href: "/examples/threejs/particles",
        desc: "CSS3DSprite vs R3F WebGL 粒子"
      },
      {
        title: "GLTF 模型",
        href: "/examples/threejs/gltf",
        desc: "GLTFLoader + HDR 环境光 + 动画"
      },
      {
        title: "粒子波浪",
        href: "/examples/threejs/waves",
        desc: "2500 粒子正弦波浪场 + 自定义 GLSL + 鼠标追踪"
      },
      {
        title: "全球视图",
        href: "/examples/threejs/globe",
        desc: "3D 地球 + 航线弧线 + 日夜切换"
      }
    ]
  },
  {
    category: "UI / 样式",
    items: [
      { title: "Dashboard", href: "/examples/dashboard", desc: "仪表盘预设布局（只读·模拟数据）" },
      { title: "Banking", href: "/examples/banking", desc: "shadcn + ECharts 复刻 banking 仪表盘" },
      { title: "Style Showcase", href: "/examples/style-showcase", desc: "组件库样式展示" },
      { title: "Tech Style", href: "/examples/tech-style", desc: "科技感 UI 风格" },
      { title: "Lottie 动画", href: "/examples/lottie", desc: "Lottie 图标与动画" },
      { title: "图片处理", href: "/examples/image", desc: "图片上传、裁剪、预览" }
    ]
  },
  {
    category: "表单 / 数据",
    items: [
      { title: "表单示例", href: "/examples/form", desc: "RHF + Zod 表单验证" },
      { title: "GraphQL", href: "/examples/graphql", desc: "GraphQL 查询与订阅" },
      {
        title: "Next.js 特性",
        href: "/examples/nextjs-features",
        desc: "Server Actions / 流式渲染"
      }
    ]
  },
  {
    category: "AI / 实时",
    items: [
      { title: "Assistant UI", href: "/examples/assistant-ui", desc: "AI 对话组件集成" },
      {
        title: "AgentScope 示例",
        href: "/examples/agentscope",
        desc: "AgentScope 多 Agent 示例（需后端开启）"
      },
      { title: "语音识别 ASR", href: "/examples/asr", desc: "实时语音转文字" },
      { title: "Omni Realtime", href: "/examples/omni-realtime", desc: "多模态实时交互" }
    ]
  },
  {
    category: "状态管理",
    items: [
      { title: "Zustand 综合", href: "/examples/zustand", desc: "Zustand + R3F 场景联动" },
      { title: "PDF 预览", href: "/examples/pdf", desc: "PDF.js 文档预览" },
      { title: "国际化 i18n", href: "/examples/i18n", desc: "next-intl 多语言切换" }
    ]
  }
]

export default function ExamplesPage() {
  return (
    <div className="mx-auto max-w-5xl p-8">
      <h1 className="mb-2 font-bold text-3xl">示例库</h1>
      <p className="mb-10 text-muted-foreground">技术预研与组件演示</p>

      <div className="space-y-10">
        {EXAMPLES.map((group) => (
          <section key={group.category}>
            <h2 className="mb-4 font-semibold text-lg">{group.category}</h2>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-3">
              {group.items.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="rounded-lg border bg-card p-4 transition-colors hover:bg-accent"
                >
                  <div className="font-medium text-sm">{item.title}</div>
                  <div className="mt-1 text-muted-foreground text-xs">{item.desc}</div>
                </Link>
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  )
}
