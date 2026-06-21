/**
 * Lottie 动画示例页
 * @author AaronZZH
 */

"use client"

import { useState } from "react"
import { toast } from "sonner"
import { LottieIcon } from "@/components/animate/LottieIcon"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Separator } from "@/components/ui/separator"

// 基础演示用文件
const ANIM = "lupuorrc"

function DemoCard({
  title,
  desc,
  children
}: {
  title: string
  desc?: string
  children: React.ReactNode
}) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{title}</CardTitle>
        {desc && <p className="text-muted-foreground text-xs">{desc}</p>}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

// ─── 场景：弹窗（成功/失败/警告） ────────────────────────────────────────────
type DialogType = "success" | "error" | "warning" | null

const DIALOG_CONFIG = {
  success: {
    label: "成功",
    icon: "success",
    title: "操作成功",
    desc: "数据已保存，一切就绪。",
    confirm: "好的"
  },
  error: {
    label: "失败",
    icon: "error",
    title: "操作失败",
    desc: "发生了错误，请稍后重试。",
    confirm: "知道了"
  },
  warning: {
    label: "警告",
    icon: "warning",
    title: "确认删除？",
    desc: "此操作不可撤销，确定要继续吗？",
    confirm: "继续"
  }
}

function DialogDemo() {
  const [type, setType] = useState<DialogType>(null)
  const cfg = type ? DIALOG_CONFIG[type] : null

  // 成功/失败用 Dialog（结果展示），警告用 AlertDialog（危险确认）
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <Button size="sm" onClick={() => setType("success")}>
          成功弹窗
        </Button>
        <Button size="sm" variant="destructive" onClick={() => setType("error")}>
          失败弹窗
        </Button>
        <Button size="sm" variant="outline" onClick={() => setType("warning")}>
          警告确认
        </Button>
      </div>

      {/* 成功 / 失败 */}
      <Dialog open={type === "success" || type === "error"} onOpenChange={() => setType(null)}>
        <DialogContent className="max-w-xs text-center">
          <DialogHeader className="items-center">
            <LottieIcon name={cfg?.icon ?? ANIM} width={80} height={80} loop={false} />
            <DialogTitle>{cfg?.title}</DialogTitle>
            <DialogDescription>{cfg?.desc}</DialogDescription>
          </DialogHeader>
          <DialogFooter className="sm:justify-center">
            <Button onClick={() => setType(null)}>{cfg?.confirm}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 警告确认 */}
      <AlertDialog open={type === "warning"} onOpenChange={() => setType(null)}>
        <AlertDialogContent className="max-w-xs text-center">
          <AlertDialogHeader className="items-center">
            <LottieIcon name="warning" width={80} height={80} loop />
            <AlertDialogTitle>{DIALOG_CONFIG.warning.title}</AlertDialogTitle>
            <AlertDialogDescription>{DIALOG_CONFIG.warning.desc}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter className="sm:justify-center">
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              onClick={() => setType(null)}
            >
              继续
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

function FeedbackDemo() {
  const [state, setState] = useState<"idle" | "success" | "error">("idle")
  return (
    <div className="flex flex-col items-center gap-4">
      {state !== "idle" && (
        <LottieIcon
          name={state === "success" ? "success" : "error"}
          width={64}
          height={64}
          loop={false}
          onComplete={() => setTimeout(() => setState("idle"), 600)}
        />
      )}
      {state === "idle" && <div className="h-16 w-16" />}
      <div className="flex gap-2">
        <Button size="sm" onClick={() => setState("success")}>
          模拟成功
        </Button>
        <Button size="sm" variant="destructive" onClick={() => setState("error")}>
          模拟失败
        </Button>
      </div>
      {state !== "idle" && (
        <Badge variant={state === "success" ? "default" : "destructive"}>
          {state === "success" ? "操作成功" : "操作失败"}
        </Badge>
      )}
    </div>
  )
}

// ─── 场景：AI 思考中 ──────────────────────────────────────────────────────────
function AIThinkingDemo() {
  const [thinking, setThinking] = useState(false)
  return (
    <div className="flex flex-col items-center gap-4">
      <div className="flex h-[72px] items-center justify-center">
        {thinking ? (
          <div className="flex items-center gap-3 rounded-full border bg-muted/50 px-4 py-2">
            <LottieIcon name="voice" width={48} height={48} loop />
            <span className="text-muted-foreground text-sm">AI 正在思考…</span>
          </div>
        ) : (
          <p className="text-muted-foreground text-sm">等待响应</p>
        )}
      </div>
      <Button size="sm" variant="outline" onClick={() => setThinking((v) => !v)}>
        {thinking ? "停止" : "发送消息"}
      </Button>
    </div>
  )
}

// ─── 场景：空状态 ─────────────────────────────────────────────────────────────
function EmptyStateDemo() {
  return (
    <div className="flex flex-col items-center gap-3 py-4 text-center">
      <LottieIcon name="cat" width={120} height={120} loop />
      <p className="font-medium text-sm">暂无数据</p>
      <p className="text-muted-foreground text-xs">还没有任何内容，点击下方按钮创建第一个</p>
      <Button size="sm">立即创建</Button>
    </div>
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

        {/* ── 基础能力 ── */}
        <div>
          <h2 className="mb-4 font-semibold text-lg">基础能力</h2>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            <DemoCard title="播放控制">
              <div className="flex flex-col items-center gap-4">
                <LottieIcon name={ANIM} width={120} height={120} loop autoplay={playing} />
                <Button size="sm" variant="outline" onClick={() => setPlaying((v) => !v)}>
                  {playing ? "暂停" : "播放"}
                </Button>
              </div>
            </DemoCard>

            <DemoCard title="鼠标悬停播放" desc="悬停时播放，离开时归零">
              <div className="flex flex-col items-center gap-4">
                <LottieIcon name={ANIM} width={120} height={120} loop={false} playOnHover />
                <p className="text-muted-foreground text-sm">悬停图标试试</p>
              </div>
            </DemoCard>

            <DemoCard title="不同尺寸">
              <div className="flex items-end justify-around gap-2">
                {[40, 80, 120].map((size) => (
                  <div key={size} className="flex flex-col items-center gap-1">
                    <LottieIcon name={ANIM} width={size} height={size} />
                    <span className="text-muted-foreground text-xs">{size}px</span>
                  </div>
                ))}
              </div>
            </DemoCard>

            <DemoCard title="渲染器对比">
              <div className="space-y-3">
                {(["svg", "canvas"] as const).map((r) => (
                  <div key={r} className="flex items-center gap-4">
                    <Badge variant="outline" className="w-16 justify-center font-mono text-xs">
                      {r}
                    </Badge>
                    <LottieIcon name={ANIM} width={80} height={80} renderer={r} />
                  </div>
                ))}
              </div>
            </DemoCard>

            <DemoCard title="Dashboard 动画">
              <div className="flex items-end justify-around gap-2">
                {(["dashboard", "dashboard-1"] as const).map((name) => (
                  <div key={name} className="flex flex-col items-center gap-1">
                    <LottieIcon name={name} width={120} height={120} />
                    <span className="font-mono text-muted-foreground text-xs">{name}</span>
                  </div>
                ))}
              </div>
            </DemoCard>
          </div>
        </div>

        <Separator />

        {/* ── 使用场景 ── */}
        <div>
          <h2 className="mb-1 font-semibold text-lg">使用场景</h2>
          <p className="mb-4 text-muted-foreground text-sm">
            以下场景当前均使用占位动画（
            <code className="rounded bg-muted px-1 text-xs">{ANIM}</code>），替换为对应场景专属
            Lottie 文件即可。
          </p>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            {/* 场景 1：SplashScreen */}
            <DemoCard title="SplashScreen" desc="应用初始化时全屏显示，替换现有 CSS pulse + spin">
              <div className="flex flex-col items-center gap-3 rounded-xl bg-background py-4">
                <LottieIcon name="404" width={240} height={240} loop />
              </div>
            </DemoCard>

            {/* 场景 2：空状态 */}
            <DemoCard title="空状态 Empty State" desc="知识库、工作流、通知列表等无数据时展示">
              <EmptyStateDemo />
            </DemoCard>

            {/* 场景 3：Coming Soon */}
            <DemoCard
              title="即将上线 Coming Soon"
              desc="替换静态火箭 webp 插画，配合倒计时更有氛围"
            >
              <div className="flex flex-col items-center gap-2">
                <LottieIcon name="design" width={140} height={140} loop />
              </div>
            </DemoCard>

            {/* 场景 4：AI 语音对话中 */}
            <DemoCard title="AI 语音中" desc="聊天/工作流执行/AIGC 生成时的 loading 状态">
              <AIThinkingDemo />
            </DemoCard>

            {/* 场景 5：操作反馈 */}
            <DemoCard title="操作成功/失败反馈" desc="Toast 或内联反馈，loop=false 播一次后消失">
              <FeedbackDemo />
            </DemoCard>

            {/* 场景 6：导航图标 hover */}
            <DemoCard
              title="导航图标 Hover"
              desc="侧边栏菜单图标悬停时播放，离开归零（playOnHover）"
            >
              <div className="flex items-center gap-6">
                {["仪表盘", "知识库", "工作流", "设置"].map((label) => (
                  <div key={label} className="flex flex-col items-center gap-1">
                    <LottieIcon name={ANIM} width={32} height={32} loop={false} playOnHover />
                    <span className="text-muted-foreground text-xs">{label}</span>
                  </div>
                ))}
              </div>
            </DemoCard>

            {/* 场景 7：弹窗（成功/失败/警告） */}
            <DemoCard
              title="弹窗反馈（成功 / 失败 / 警告确认）"
              desc="结果类用 Dialog，危险操作确认用 AlertDialog，顶部插画替代静态图标"
            >
              <DialogDemo />
            </DemoCard>

            {/* 场景 8：Toast */}
            <DemoCard
              title="Toast 通知"
              desc="用 toast.custom() 内嵌 Lottie，loop=false 播一次（成功/失败），loop 循环（警告）"
            >
              <div className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  onClick={() =>
                    toast.custom((id) => (
                      <div className="flex w-72 items-center gap-3 rounded-lg bg-background px-3">
                        <LottieIcon name="success" width={48} height={48} loop={false} />
                        <div className="min-w-0 flex-1">
                          <p className="font-medium text-sm">操作成功</p>
                          <p className="truncate text-muted-foreground text-xs">
                            文件已上传到知识库
                          </p>
                        </div>
                        <button
                          type="button"
                          className="text-lg text-muted-foreground leading-none hover:text-foreground"
                          onClick={() => toast.dismiss(id)}
                        >
                          ×
                        </button>
                      </div>
                    ))
                  }
                >
                  成功
                </Button>
                <Button
                  size="sm"
                  variant="destructive"
                  onClick={() =>
                    toast.custom((id) => (
                      <div className="flex w-72 items-center gap-3 rounded-lg border-destructive/30 bg-background px-3">
                        <LottieIcon name="error" width={48} height={48} loop={false} />
                        <div className="min-w-0 flex-1">
                          <p className="font-medium text-destructive text-sm">操作失败</p>
                          <p className="truncate text-muted-foreground text-xs">
                            网络连接超时，请重试
                          </p>
                        </div>
                        <button
                          type="button"
                          className="text-lg text-muted-foreground leading-none hover:text-foreground"
                          onClick={() => toast.dismiss(id)}
                        >
                          ×
                        </button>
                      </div>
                    ))
                  }
                >
                  失败
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() =>
                    toast.custom((id) => (
                      <div className="flex w-72 items-center gap-3 rounded-lg border-yellow-500/30 bg-background px-3">
                        <LottieIcon name="warning" width={48} height={48} loop />
                        <div className="min-w-0 flex-1">
                          <p className="font-medium text-sm">注意</p>
                          <p className="truncate text-muted-foreground text-xs">
                            此操作不可撤销，请谨慎
                          </p>
                        </div>
                        <button
                          type="button"
                          className="text-lg text-muted-foreground leading-none hover:text-foreground"
                          onClick={() => toast.dismiss(id)}
                        >
                          ×
                        </button>
                      </div>
                    ))
                  }
                >
                  警告
                </Button>
              </div>
            </DemoCard>
          </div>
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
                ["name", "string", "—", "文件名，加载 /assets/icons/lottie/{name}.json"],
                ["width / height", "number | string", "300", "容器尺寸"],
                ["loop", "boolean", "true", "是否循环播放"],
                ["autoplay", "boolean", "true", "是否自动播放"],
                ["renderer", "svg | canvas | html", "svg", "渲染器类型"],
                ["playOnHover", "boolean", "false", "悬停时播放，离开归零"],
                ["onComplete", "() => void", "—", "动画完成回调（配合 loop=false 使用）"],
                ["onLoopComplete", "() => void", "—", "每次循环完成回调"]
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
