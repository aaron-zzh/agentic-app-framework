/**
 * 技能选择弹窗内容
 *
 * 展示可选技能列表（按 category 筛选：IMAGE_GEN/COPYWRITING/VIDEO_GEN），
 * 选中后将 skill 存入 aigc store，task 提交时携带其 systemPrompt。
 *
 * 使用方式：
 * ```tsx
 * <Popover>
 *   <PopoverTrigger render={<button type="button" />}>技能</PopoverTrigger>
 *   <PopoverContent>
 *     <SkillPickerContent onClose={() => setOpen(false)} />
 *   </PopoverContent>
 * </Popover>
 * ```
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Check, Image, Search, Type, Zap } from "lucide-react"
import { useState } from "react"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { useAigcStore } from "@/features/aigc/store"
import type { AiSkillVO } from "@/lib/queries/use-ai-skills"
import { useAiSkills } from "@/lib/queries/use-ai-skills"
import { cn } from "@/lib/utils"

// ─── 分类配置 ────────────────────────────────────────────────────────────────

const CATEGORY_TABS: Array<{
  key: string | null
  label: string
  icon: React.FC<{ className?: string }>
}> = [
  { key: null, label: "全部", icon: Zap },
  { key: "IMAGE_GEN", label: "生图", icon: Image },
  { key: "COPYWRITING", label: "文案", icon: Type }
]

// ─── 技能卡片 ────────────────────────────────────────────────────────────────

function SkillCard({
  skill,
  selected,
  onSelect
}: {
  skill: AiSkillVO
  selected: boolean
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        "flex w-full items-start gap-3 rounded-xl border px-3 py-2.5 text-left transition-colors",
        selected
          ? "border-primary/40 bg-primary/10"
          : "border-foreground/6 bg-foreground/2 hover:bg-foreground/5"
      )}
    >
      {/* 技能名首字作图标 */}
      <div
        className={cn(
          "mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg font-semibold text-sm",
          selected ? "bg-primary/20 text-primary" : "bg-foreground/8 text-foreground/60"
        )}
      >
        {skill.name.slice(0, 1)}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <span className="truncate font-medium text-sm">{skill.name}</span>
          {selected && <Check className="size-3.5 shrink-0 text-primary" />}
        </div>
        {skill.description && (
          <p className="mt-0.5 line-clamp-2 text-muted-foreground text-xs">{skill.description}</p>
        )}
      </div>
    </button>
  )
}

// ─── 主组件 ─────────────────────────────────────────────────────────────────

interface SkillPickerContentProps {
  /** 约束只展示与当前 feature 匹配的 category，null=不约束 */
  defaultCategory?: string | null
  onClose?: () => void
}

export function SkillPickerContent({ defaultCategory, onClose }: SkillPickerContentProps) {
  const [activeCategory, setActiveCategory] = useState<string | null>(defaultCategory ?? null)
  const [search, setSearch] = useState("")

  const selectedSkill = useAigcStore((s) => s.selectedSkill)
  const setSelectedSkill = useAigcStore((s) => s.setSelectedSkill)

  const { data: skills, isLoading } = useAiSkills({ activeOnly: true })

  // 本地筛选
  const filtered = (skills ?? []).filter((s) => {
    const matchCategory = activeCategory ? s.category === activeCategory : true
    const matchSearch = search
      ? s.name.includes(search) || (s.description ?? "").includes(search)
      : true
    return matchCategory && matchSearch
  })

  const handleSelect = (skill: AiSkillVO) => {
    // 再次点击同一技能 → 取消选中
    setSelectedSkill(selectedSkill?.id === skill.id ? null : skill)
    onClose?.()
  }

  return (
    <div className="flex flex-col">
      {/* 标题 */}
      <div className="flex items-center justify-between border-foreground/6 border-b px-4 py-3">
        <span className="font-semibold text-sm">选择技能</span>
        {selectedSkill && (
          <button
            type="button"
            onClick={() => {
              setSelectedSkill(null)
              onClose?.()
            }}
            className="text-muted-foreground text-xs hover:text-foreground"
          >
            清除
          </button>
        )}
      </div>

      {/* 分类 Tab */}
      <div className="flex gap-1 border-foreground/6 border-b px-3 py-2">
        {CATEGORY_TABS.map((tab) => (
          <button
            key={tab.key ?? "all"}
            type="button"
            onClick={() => setActiveCategory(tab.key)}
            className={cn(
              "flex items-center gap-1 rounded-full px-2.5 py-1 text-xs transition-colors",
              activeCategory === tab.key
                ? "bg-primary/15 text-primary"
                : "text-muted-foreground hover:bg-foreground/6 hover:text-foreground"
            )}
          >
            <tab.icon className="size-3" />
            {tab.label}
          </button>
        ))}
      </div>

      {/* 搜索 */}
      <div className="px-3 py-2">
        <div className="relative">
          <Search className="absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索技能..."
            className="h-8 pl-8 text-xs"
          />
        </div>
      </div>

      {/* 技能列表 */}
      <ScrollArea className="h-[300px]">
        <div className="flex flex-col gap-1.5 px-3 pb-3">
          {isLoading ? (
            Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={`sk-${i}`} className="h-16 w-full rounded-xl" />
            ))
          ) : filtered.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground text-sm">暂无可用技能</p>
          ) : (
            filtered.map((skill) => (
              <SkillCard
                key={skill.id}
                skill={skill}
                selected={selectedSkill?.id === skill.id}
                onSelect={() => handleSelect(skill)}
              />
            ))
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
