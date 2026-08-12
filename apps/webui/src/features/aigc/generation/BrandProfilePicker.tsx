/**
 * 品牌资料选择器：仅应用当前已发布版本的品牌规则。
 * @author AaronZZH & Kiro
 */

"use client"

import { BadgeCheck, Building2, Search, UserRound, X } from "lucide-react"
import { useMemo, useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import {
  type AigcBrandProfileSelection,
  aigcBrandApi,
  createAigcBrandSystemPrompt,
  useAigcBrandProfiles
} from "@/lib/api/rest/ai/aigc"
import { cn } from "@/lib/utils/cn"

interface BrandProfilePickerProps {
  value: AigcBrandProfileSelection | null
  onChange: (selection: AigcBrandProfileSelection | null) => void
  triggerClassName?: string
}

const KIND_LABELS = {
  enterprise: "企业品牌",
  sub_brand: "子品牌",
  product_line: "产品线",
  personal_ip: "个人 IP"
} as const

/** 以技能选择器同款 Popover 选择已发布的品牌资料，并作为系统规则应用。 */
export function BrandProfilePicker({ value, onChange, triggerClassName }: BrandProfilePickerProps) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState("")
  const [selectingProfileId, setSelectingProfileId] = useState<number | null>(null)
  const { data: page, isLoading } = useAigcBrandProfiles()

  const profiles = useMemo(
    () => (page?.list ?? []).filter((profile) => profile.currentVersionId !== undefined),
    [page?.list]
  )
  const visibleProfiles = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    if (!normalizedSearch) return profiles
    return profiles.filter((profile) =>
      `${profile.name} ${profile.industry ?? ""} ${KIND_LABELS[profile.kind]}`
        .toLowerCase()
        .includes(normalizedSearch)
    )
  }, [profiles, search])

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen)
    if (nextOpen) setSearch("")
  }

  async function applyProfile(profileId: number) {
    const profile = profiles.find((candidate) => candidate.id === profileId)
    if (!profile?.currentVersionId) return

    setSelectingProfileId(profile.id)
    try {
      const versions = await aigcBrandApi.versions(profile.id)
      const version = versions.find(
        (candidate) => candidate.id === profile.currentVersionId && candidate.status === "published"
      )
      if (!version) {
        toast.error("该品牌资料当前没有可应用的已发布版本")
        return
      }
      onChange({
        profileId: profile.id,
        versionId: version.id,
        name: profile.name,
        systemPrompt: createAigcBrandSystemPrompt(profile, version)
      })
      setOpen(false)
    } catch {
      // API 客户端已统一提示请求错误
    } finally {
      setSelectingProfileId(null)
    }
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger
        render={
          <button
            type="button"
            className={
              triggerClassName ??
              "inline-flex h-7 items-center gap-1 rounded-md px-2 text-muted-foreground text-xs hover:bg-accent hover:text-foreground"
            }
          />
        }
      >
        <Building2 className="size-3" />
        <span className="max-w-20 truncate">{value?.name ?? "品牌"}</span>
      </PopoverTrigger>
      <PopoverContent align="end" sideOffset={6} className="w-[28rem] max-w-[calc(100vw-2rem)] p-0">
        <div className="flex items-center justify-between border-foreground/6 border-b px-4 py-3">
          <div>
            <p className="font-semibold text-sm">选择品牌资料</p>
            <p className="mt-0.5 text-muted-foreground text-xs">仅可应用当前已发布版本</p>
          </div>
          <div className="flex items-center gap-2">
            {value ? <Badge variant="secondary">已应用</Badge> : null}
            {value ? (
              <button
                type="button"
                onClick={() => onChange(null)}
                className="flex items-center gap-1 rounded-md px-2 py-1 text-muted-foreground text-xs hover:bg-foreground/6 hover:text-foreground"
              >
                <X className="size-3" />
                清除
              </button>
            ) : null}
          </div>
        </div>
        <div className="border-foreground/6 border-b p-3">
          <div className="relative">
            <Search className="absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="搜索品牌、IP 或行业..."
              className="h-8 pl-8 text-xs"
            />
          </div>
        </div>
        <ScrollArea className="h-[300px]">
          <div className="flex flex-col gap-1.5 p-3">
            {isLoading ? (
              Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={`brand-skeleton-${index}`} className="h-16 w-full rounded-xl" />
              ))
            ) : visibleProfiles.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground text-sm">
                {search.trim()
                  ? "没有匹配的品牌资料"
                  : "暂无已发布的品牌资料，请先在资产中心发布版本"}
              </p>
            ) : (
              visibleProfiles.map((profile) => {
                const selected = value?.profileId === profile.id
                const Icon = profile.kind === "personal_ip" ? UserRound : Building2
                return (
                  <button
                    key={profile.id}
                    type="button"
                    disabled={selectingProfileId !== null}
                    aria-pressed={selected}
                    onClick={() => void applyProfile(profile.id)}
                    className={cn(
                      "flex w-full items-start gap-3 rounded-xl border px-3 py-2.5 text-left transition-colors disabled:cursor-wait disabled:opacity-60",
                      selected
                        ? "border-primary/40 bg-primary/10"
                        : "border-foreground/6 bg-foreground/2 hover:bg-foreground/5"
                    )}
                  >
                    <div
                      className={cn(
                        "mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg",
                        selected
                          ? "bg-primary/20 text-primary"
                          : "bg-foreground/8 text-foreground/60"
                      )}
                    >
                      <Icon className="size-4" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5">
                        <span className="truncate font-medium text-sm">{profile.name}</span>
                        {selected ? (
                          <BadgeCheck className="size-3.5 shrink-0 text-primary" />
                        ) : null}
                      </div>
                      <p className="mt-0.5 truncate text-muted-foreground text-xs">
                        {KIND_LABELS[profile.kind]}
                        {profile.industry ? ` · ${profile.industry}` : ""}
                      </p>
                    </div>
                    <span className="pt-0.5 text-muted-foreground text-xs">
                      {selectingProfileId === profile.id ? "应用中…" : "已发布"}
                    </span>
                  </button>
                )
              })
            )}
          </div>
        </ScrollArea>
      </PopoverContent>
    </Popover>
  )
}
