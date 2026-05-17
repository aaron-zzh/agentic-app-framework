"use client"

/**
 * Toast / Snackbar 示例页
 * 路由：/dev/toast
 * @author AaronZZH & Kiro
 */

import { toast } from "sonner"
import { PageContainer } from "@/components/common/PageContainer"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { TypographyH1 } from "@/components/ui/typography"
import { notify } from "@/lib/notification"

const POSITIONS = [
  "top-left",
  "top-center",
  "top-right",
  "bottom-left",
  "bottom-center",
  "bottom-right"
] as const

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-3">
      <h2 className="font-semibold text-muted-foreground text-xs uppercase tracking-wide">
        {title}
      </h2>
      <div className="flex flex-wrap items-start gap-3">{children}</div>
      <Separator />
    </div>
  )
}

export default function ToastPage() {
  const handlePromise = async () => {
    const promise = new Promise((resolve) => setTimeout(resolve, 2000))
    notify.promise(promise, {
      loading: "处理中...",
      success: "操作成功！",
      error: "操作失败"
    })
    await promise
  }

  return (
    <PageContainer>
      <TypographyH1 className="mb-6 text-2xl">Toast / Snackbar</TypographyH1>
      <div className="space-y-8">
        {/* 状态类型 */}
        <Section title="Status">
          <Button variant="outline" onClick={() => notify.success("保存成功")}>
            Success
          </Button>
          <Button variant="outline" onClick={() => notify.error("保存失败")}>
            Error
          </Button>
          <Button variant="outline" onClick={() => notify.warning("注意：操作不可逆")}>
            Warning
          </Button>
          <Button variant="outline" onClick={() => notify.info("系统将于今晚维护")}>
            Info
          </Button>
          <Button variant="outline" onClick={() => toast("这是一条默认消息")}>
            Default
          </Button>
        </Section>

        {/* 带描述 */}
        <Section title="With Description">
          <Button
            variant="outline"
            onClick={() =>
              notify.success("文件上传成功", {
                description: "document.pdf 已上传到知识库"
              })
            }
          >
            Success + Description
          </Button>
          <Button
            variant="outline"
            onClick={() =>
              notify.error("网络请求失败", {
                description: "请检查网络连接后重试（错误码 503）"
              })
            }
          >
            Error + Description
          </Button>
        </Section>

        {/* Action 按钮 */}
        <Section title="With Action">
          <Button
            variant="outline"
            onClick={() =>
              notify.success("已删除 3 条记录", {
                action: {
                  label: "撤销",
                  onClick: () => notify.info("已撤销删除")
                }
              })
            }
          >
            删除 + 撤销
          </Button>
          <Button
            variant="outline"
            onClick={() =>
              notify.warning("确认归档？", {
                duration: 0,
                action: {
                  label: "确认",
                  onClick: () => notify.success("已归档")
                }
              })
            }
          >
            不自动关闭 (duration=0)
          </Button>
        </Section>

        {/* Loading / Promise */}
        <Section title="Loading & Promise">
          <Button
            variant="outline"
            onClick={() => {
              notify.loading("正在保存...", { id: "save-demo" })
              setTimeout(() => {
                notify.success("保存成功", { id: "save-demo" })
              }, 2000)
            }}
          >
            Loading → Success
          </Button>
          <Button variant="outline" onClick={handlePromise}>
            Promise
          </Button>
        </Section>

        {/* 位置 */}
        <Section title="Anchor Origin (Position)">
          {POSITIONS.map((pos) => (
            <Button
              key={pos}
              variant="outline"
              size="sm"
              onClick={() => toast(pos, { position: pos })}
            >
              {pos}
            </Button>
          ))}
        </Section>

        {/* 自定义 */}
        <Section title="Custom">
          <Button
            variant="outline"
            onClick={() =>
              toast("自定义内容", {
                description: "支持传入任意 ReactNode",
                icon: "🎉",
                duration: 5000
              })
            }
          >
            Custom Icon (emoji)
          </Button>
          <Button
            variant="outline"
            onClick={() =>
              notify.error("操作失败", {
                description: "错误详情：数据库连接超时",
                duration: 0,
                action: {
                  label: "重试",
                  onClick: () => notify.loading("重试中...")
                }
              })
            }
          >
            Error + 不关闭 + 重试
          </Button>
        </Section>
      </div>
    </PageContainer>
  )
}
