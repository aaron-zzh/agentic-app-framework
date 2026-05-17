"use client"

/**
 * Toast / Snackbar 示例页
 * 路由：/dev/toast
 * @author AaronZZH & Kiro
 */

import { toast } from "sonner"
import { ComponentBox, ComponentLayout } from "@/components/common/ComponentLayout"
import { Button } from "@/components/ui/button"
import { notify } from "@/lib/notification"

const POSITIONS = [
  "top-left", "top-center", "top-right",
  "bottom-left", "bottom-center", "bottom-right"
] as const

export default function ToastPage() {
  const handlePromise = async () => {
    const promise = new Promise((resolve) => setTimeout(resolve, 2000))
    notify.promise(promise, { loading: "处理中...", success: "操作成功！", error: "操作失败" })
    await promise
  }

  return (
    <ComponentLayout
      heading="Toast / Snackbar"
      description="基于 sonner 的全局通知系统，通过 notify 工具函数触发。"
      links={[
        { name: "sonner", href: "https://sonner.emilkowal.ski/" },
        { name: "shadcn/ui", href: "https://ui.shadcn.com/docs/components/sonner" }
      ]}
      sectionData={[
        {
          name: "Status",
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
          name: "With Description",
          description: "带副标题的通知",
          component: (
            <ComponentBox>
              <Button
                variant="outline"
                onClick={() => notify.success("文件上传成功", { description: "document.pdf 已上传到知识库" })}
              >
                Success + Description
              </Button>
              <Button
                variant="outline"
                onClick={() => notify.error("网络请求失败", { description: "请检查网络连接后重试（错误码 503）" })}
              >
                Error + Description
              </Button>
            </ComponentBox>
          )
        },
        {
          name: "With Action",
          description: "带操作按钮，有 action 时自动隐藏关闭按钮",
          component: (
            <ComponentBox>
              <Button
                variant="outline"
                onClick={() =>
                  notify.success("已删除 3 条记录", {
                    action: { label: "撤销", onClick: () => notify.info("已撤销删除") }
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
                    action: { label: "确认", onClick: () => notify.success("已归档") }
                  })
                }
              >
                不自动关闭 (duration=0)
              </Button>
            </ComponentBox>
          )
        },
        {
          name: "Loading & Promise",
          component: (
            <ComponentBox>
              <Button
                variant="outline"
                onClick={() => {
                  notify.loading("正在保存...", { id: "save-demo" })
                  setTimeout(() => notify.success("保存成功", { id: "save-demo" }), 2000)
                }}
              >
                Loading → Success
              </Button>
              <Button variant="outline" onClick={handlePromise}>Promise</Button>
            </ComponentBox>
          )
        },
        {
          name: "Anchor Origin",
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
        },
        {
          name: "Custom",
          component: (
            <ComponentBox>
              <Button
                variant="outline"
                onClick={() => toast("自定义内容", { description: "支持传入任意 ReactNode", icon: "🎉" })}
              >
                Custom Icon (emoji)
              </Button>
              <Button
                variant="outline"
                onClick={() =>
                  notify.error("操作失败", {
                    description: "数据库连接超时",
                    duration: 0,
                    action: { label: "重试", onClick: () => notify.loading("重试中...") }
                  })
                }
              >
                Error + 不关闭 + 重试
              </Button>
            </ComponentBox>
          )
        }
      ]}
    />
  )
}
