/**
 * Lottie 动画示例页——参考 tmp/nextjs/xueji/apps/demo/src/app/(demo)/lottie
 * @author Kiro
 */

"use client"

import { useState } from "react"
import { LottieIcon } from "@/components/animate/LottieIcon"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"

// ─── Demo 区块 ────────────────────────────────────────────────────────────────

function DemoCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

export default function LottieDemoPage() {
  const [playing, setPlaying] = useState(true)

  return (
    <div className="min-h-screen p-8">
      <div className="mx-auto max-w-4xl space-y-8">
        <div>
          <h1 className="font-bold text-3xl">Lottie 动画</h1>
          <p className="mt-1 text-muted-foreground">
            基于 <code className="rounded bg-muted px-1 text-sm">lottie-web</code> 的动画组件，
            支持内联数据或 JSON 文件两种方式。
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          {/* 播放控制 */}
          <DemoCard title="播放控制">
            <div className="flex flex-col items-center gap-4">
              <LottieIcon name="lupuorrc" width={120} height={120} loop autoplay={playing} />
              <Button size="sm" variant="outline" onClick={() => setPlaying((v) => !v)}>
                {playing ? "暂停" : "播放"}
              </Button>
            </div>
          </DemoCard>

          {/* 悬停播放 */}
          <DemoCard title="鼠标悬停播放">
            <div className="flex flex-col items-center gap-4">
              <LottieIcon name="lupuorrc" width={120} height={120} loop={false} playOnHover />
              <p className="text-muted-foreground text-sm">悬停时播放，离开时归零</p>
            </div>
          </DemoCard>

          {/* 不同尺寸 */}
          <DemoCard title="不同尺寸">
            <div className="flex items-end justify-around gap-2">
              {[40, 80, 120].map((size) => (
                <div key={size} className="flex flex-col items-center gap-1">
                  <LottieIcon name="lupuorrc" width={size} height={size} />
                  <span className="text-muted-foreground text-xs">{size}px</span>
                </div>
              ))}
            </div>
          </DemoCard>

          {/* 渲染器对比 */}
          <DemoCard title="渲染器对比">
            <div className="space-y-3">
              {(["svg", "canvas"] as const).map((r) => (
                <div key={r} className="flex items-center gap-4">
                  <Badge variant="outline" className="w-16 justify-center font-mono text-xs">
                    {r}
                  </Badge>
                  <LottieIcon name="lupuorrc" width={80} height={80} renderer={r} />
                </div>
              ))}
            </div>
          </DemoCard>
        </div>

        <Separator />

        {/* Props 说明 */}
        <div className="rounded-lg border p-6">
          <h2 className="mb-4 font-semibold">Props 参考</h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="pr-4 pb-2">Prop</th>
                <th className="pr-4 pb-2">类型</th>
                <th className="pr-4 pb-2">默认值</th>
                <th className="pb-2">说明</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {[
                ["animationData", "object", "—", "内联 JSON 数据（与 name 二选一）"],
                ["name", "string", "—", "文件名，加载 /icons/lottie/{name}.json"],
                ["width / height", "number | string", "300", "容器尺寸"],
                ["loop", "boolean", "true", "是否循环播放"],
                ["autoplay", "boolean", "true", "是否自动播放"],
                ["renderer", "svg | canvas | html", "svg", "渲染器类型"],
                ["playOnHover", "boolean", "false", "悬停时播放"],
                ["onComplete", "() => void", "—", "动画完成回调"],
                ["onLoopComplete", "() => void", "—", "循环完成回调"]
              ].map(([prop, type, def, desc]) => (
                <tr key={prop}>
                  <td className="py-2 pr-4 font-mono text-xs">{prop}</td>
                  <td className="py-2 pr-4 text-muted-foreground text-xs">{type}</td>
                  <td className="py-2 pr-4 text-muted-foreground text-xs">{def}</td>
                  <td className="py-2 text-xs">{desc}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
