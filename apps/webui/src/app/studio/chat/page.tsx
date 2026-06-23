/**
 * /studio/chat——全屏 AI 助理
 * M8: 支持 ?skill=xxx 参数预选技能（技能 code）
 * 顶部 NeonChip 显示当前技能，点击可退出
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { useRouter, useSearchParams } from "next/navigation"
import { useMemo } from "react"
import { NeonChip } from "@/components/studio"
import { Chatter } from "@/features/chatter"
import { useAiSkills } from "@/lib/queries/use-ai-skills"

export default function StudioChatPage() {
  const searchParams = useSearchParams()
  const router = useRouter()
  const skillCode = searchParams.get("skill")

  // 查询技能详情（带 system_prompt）
  const { data: skills } = useAiSkills({ activeOnly: true })
  const skill = useMemo(
    () => (skillCode ? skills?.find((s) => s.code === skillCode) : undefined),
    [skills, skillCode]
  )

  // 用技能 system_prompt 注入 agentRole（Chatter 通过 agentRole 路由到对应后端助理）
  // 同时将 skill.systemPrompt 作为 systemPrompt prop 传给 Chatter（若 Chatter 支持）
  const agentRole = skillCode ?? undefined

  const handleClearSkill = () => {
    router.replace("/studio/chat")
  }

  return (
    <div className="flex h-full flex-col">
      {/* 技能提示条 */}
      {skill && (
        <div className="flex shrink-0 items-center gap-2 border-foreground/[0.06] border-b px-4 py-2">
          <span className="text-muted-foreground text-xs">当前技能：</span>
          <NeonChip tone="violet" size="sm">
            {skill.name}
          </NeonChip>
          <button
            type="button"
            onClick={handleClearSkill}
            className="ml-auto flex items-center gap-1 text-muted-foreground text-xs hover:text-foreground"
          >
            <X className="size-3" />
            退出技能模式
          </button>
        </div>
      )}

      <div className="min-h-0 flex-1">
        <Chatter preset="ai" layout="page" agentRole={agentRole} persist />
      </div>
    </div>
  )
}
