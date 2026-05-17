"use client"

import { toast } from "sonner"
import { ComponentBox, ComponentLayout } from "@/components/common/ComponentLayout"
import { Button } from "@/components/ui/button"
import { notify } from "@/lib/notification"

const POSITIONS = [
  "top-left", "top-center", "top-right",
  "bottom-left", "bottom-center", "bottom-right"
] as const

export default function FeedbackPage() {
  const handlePromise = async () => {
    const promise = new Promise((resolve) => setTimeout(resolve, 2000))
    notify.promise(promise, { loading: "处理中...", success: "操作成功！", error: "操作失败" })
    await promise
  }

  return (
    <ComponentLayout
      heading="反馈组件"
      description="Toast 通知、加载状态等用户反馈组件。"
      links={[
        { name: "sonner", href: "https://sonner.emilkowal.ski/" },
        { name: "shadcn/ui Sonner", href: "https://ui.shadcn.com/docs/components/sonner" }
      ]}
      sectionData={[
        {
          name: "Toast Status",
          description: "四种语义类型 + 默认类型",
          component: (
            <ComponentBox>
              <Button variant="outline" onClick={() => notify.success("保存成功")}>Success</Button>
              <Button variant="outline" onClick={() => notify.error("保存失败")}>Error</Button>
              <Button variant="outline" onClick={() => notify.warning("注意：操作不可逆")}>Warning</Button>
              <Button variant="outline" onClick={() => notify.info("系统将于今晚维护")}>Info</Button>
              <Button variant="outline" onClick={() => toast("这是一条默认消息")}>Default</Button>
            </ComponentBox>
          )
        },
        {
          name: "Toast With Description",
          component: (
            <ComponentBox>
              <Button variant="outline"
                onClick={() => notify.success("文件上传成功", { description: "document.pdf 已上传到知识库" })}>
                Success + Description
              </Button>
              <Button variant="outline"
                onClick={() => notify.error("网络请求失败", { description: "请检查网络连接后重试（错误码 503）" })}>
                Error + Description
              </Button>
            </ComponentBox>
          )
        },
        {
          name: "Toast With Action",
          description: "有 action 时自动隐藏关闭按钮",
          component: (
            <ComponentBox>
              <Button variant="outline"
                onClick={() => notify.success("已删除 3 条记录", {
                  action: { label: "撤销", onClick: () => notify.info("已撤销删除") }
                })}>
                删除 + 撤销
              </Button>
              <Button variant="outline"
                onClick={() => notify.warning("确认归档？", {
                  duration: 0,
                  action: { label: "确认", onClick: () => notify.success("已归档") }
                })}>
                不自动关闭 (duration=0)
              </Button>
            </ComponentBox>
          )
        },
        {
          name: "Toast Loading & Promise",
          component: (
            <ComponentBox>
              <Button variant="outline" onClick={() => {
                notify.loading("正在保存...", { id: "save-demo" })
                setTimeout(() => notify.success("保存成功", { id: "save-demo" }), 2000)
              }}>
                Loading → Success
              </Button>
              <Button variant="outline" onClick={handlePromise}>Promise</Button>
            </ComponentBox>
          )
        },
        {
          name: "Toast Anchor Origin",
          description: "支持 6 个位置，每次调用时传入 position 覆盖默认值",
          component: (
            <ComponentBox>
              {POSITIONS.map((pos) => (
                <Button key={pos} variant="outline" size="sm" onClick={() => toast(pos, { position: pos })}>
                  {pos}
                </Button>
              ))}
            </ComponentBox>
          )
        }
      ]}
    />
  )
}
