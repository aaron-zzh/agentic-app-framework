/**
 * BranchSwitcher——对话分支切换器
 * 使用 assistant-ui BranchPicker primitive 实现分支导航
 * 支持编辑消息触发重新生成
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  BranchPickerPrimitive,
  ComposerPrimitive,
  MessagePrimitive,
  useEditComposer,
  useMessageRuntime
} from "@assistant-ui/react"
import { ChevronLeft, ChevronRight, Pencil } from "lucide-react"
import { Button } from "@/components/ui/button"

/**
 * 分支切换器
 * 显示当前分支索引/总数，左右箭头切换
 * 需要放在 MessagePrimitive 上下文内使用
 */
export function BranchSwitcher() {
  return (
    <div className="flex items-center gap-1">
      <BranchPickerPrimitive.Previous asChild>
        <Button variant="ghost" size="xs">
          <ChevronLeft className="size-3.5" />
          <span className="sr-only">上一个分支</span>
        </Button>
      </BranchPickerPrimitive.Previous>

      <span className="text-muted-foreground text-xs tabular-nums">
        <BranchPickerPrimitive.Number /> / <BranchPickerPrimitive.Count />
      </span>

      <BranchPickerPrimitive.Next asChild>
        <Button variant="ghost" size="xs">
          <ChevronRight className="size-3.5" />
          <span className="sr-only">下一个分支</span>
        </Button>
      </BranchPickerPrimitive.Next>
    </div>
  )
}

/**
 * 消息编辑触发器
 * 点击后进入编辑模式，编辑提交后触发重新生成（新分支）
 * 需要放在 user 消息的 MessagePrimitive 上下文内
 */
export function MessageEditTrigger() {
  const messageRuntime = useMessageRuntime()

  return (
    <MessagePrimitive.If user>
      <Button variant="ghost" size="xs" onClick={() => messageRuntime.composer.beginEdit()}>
        <Pencil className="size-3" />
        <span className="sr-only">编辑消息</span>
      </Button>
    </MessagePrimitive.If>
  )
}

/**
 * 消息编辑器
 * 编辑模式下的输入框 + 提交/取消按钮
 * 提交后自动创建新分支并重新生成
 */
export function MessageEditComposer() {
  const editComposer = useEditComposer()
  const messageRuntime = useMessageRuntime()

  if (editComposer.type !== "edit") return null

  return (
    <div className="flex flex-col gap-2">
      <ComposerPrimitive.Input className="min-h-[60px] w-full resize-none rounded-md border bg-background p-2 text-sm outline-none focus:ring-1 focus:ring-ring" />
      <div className="flex justify-end gap-2">
        <Button variant="outline" size="sm" onClick={() => messageRuntime.composer.cancel()}>
          取消
        </Button>
        <ComposerPrimitive.Send asChild>
          <Button size="sm">重新生成</Button>
        </ComposerPrimitive.Send>
      </div>
    </div>
  )
}
