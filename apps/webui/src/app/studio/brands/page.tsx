/**
 * Content Studio 品牌 / IP 资料管理页。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean, useSetState } from "@aaf/hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Building2, Pencil, Plus, Trash2, UserRound } from "lucide-react"
import { useEffect, useId, useState } from "react"
import { toast } from "sonner"
import { GlassCard, GlassCardBody, GlowButton, NeonChip, SectionHaze } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle
} from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle
} from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import {
  type ContentBrandProfileInput,
  type ContentBrandProfileKind,
  type ContentBrandProfileVO,
  contentStudioApi,
  useContentBrandProfiles
} from "@/lib/api/rest/content"

const KIND_LABELS: Record<ContentBrandProfileKind, string> = {
  enterprise: "企业品牌",
  sub_brand: "子品牌",
  product_line: "产品线",
  personal_ip: "个人 IP"
}

const EMPTY_FORM: ContentBrandProfileInput = {
  name: "",
  kind: "enterprise",
  industry: "",
  logoUrl: "",
  positioning: "",
  audience: "",
  toneOfVoice: "",
  visualStyle: "",
  disclaimer: "",
  forbiddenItems: "",
  status: "published"
}

interface BrandProfileSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  profile: ContentBrandProfileVO | null
}

function BrandProfileSheet({ open, onOpenChange, profile }: BrandProfileSheetProps) {
  const id = useId()
  const queryClient = useQueryClient()
  const { state: form, setState: setForm } = useSetState<ContentBrandProfileInput>(EMPTY_FORM)
  const save = useMutation({
    mutationFn: (data: ContentBrandProfileInput) =>
      profile
        ? contentStudioApi.updateBrandProfile(profile.id, data)
        : contentStudioApi.createBrandProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["content-studio", "brand-profiles"] })
      toast.success(profile ? "品牌资料已更新" : "品牌资料已创建")
      onOpenChange(false)
    }
  })

  useEffect(() => {
    if (!open) return
    setForm(
      profile
        ? {
            name: profile.name,
            kind: profile.kind,
            industry: profile.industry ?? "",
            logoUrl: profile.logoUrl ?? "",
            positioning: profile.positioning ?? "",
            audience: profile.audience ?? "",
            toneOfVoice: profile.toneOfVoice ?? "",
            visualStyle: profile.visualStyle ?? "",
            disclaimer: profile.disclaimer ?? "",
            forbiddenItems: profile.forbiddenItems ?? "",
            profileVersion: profile.profileVersion,
            status: profile.status
          }
        : EMPTY_FORM
    )
  }, [open, profile, setForm])

  const fields: { key: keyof ContentBrandProfileInput; label: string; placeholder: string }[] = [
    { key: "positioning", label: "品牌定位", placeholder: "品牌价值、差异化与市场位置" },
    { key: "audience", label: "目标受众", placeholder: "核心人群、需求与使用场景" },
    { key: "toneOfVoice", label: "语气", placeholder: "希望内容采用的表达语气" },
    { key: "visualStyle", label: "视觉风格", placeholder: "色彩、构图、字体与参考风格" },
    { key: "disclaimer", label: "必要声明", placeholder: "内容必须包含的声明" },
    { key: "forbiddenItems", label: "禁用项", placeholder: "不得出现的表述或视觉元素" }
  ]

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="overflow-y-auto sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>{profile ? "编辑品牌 / IP 资料" : "新建品牌 / IP 资料"}</SheetTitle>
          <SheetDescription>资料会作为项目的长期品牌事实与创作约束。</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-4 px-4">
          <div className="flex flex-col gap-2">
            <label htmlFor={`${id}-name`} className="font-medium text-sm">
              名称 *
            </label>
            <Input
              id={`${id}-name`}
              value={form.name}
              onChange={(event) => setForm({ name: event.target.value })}
              placeholder="品牌、产品线或个人 IP 名称"
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-2">
              <span className="font-medium text-sm">类型</span>
              <Select
                value={form.kind}
                onValueChange={(value) =>
                  value && setForm({ kind: value as ContentBrandProfileKind })
                }
              >
                <SelectTrigger className="w-full">
                  <SelectValue>{KIND_LABELS[form.kind]}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {Object.entries(KIND_LABELS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        {label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-2">
              <label htmlFor={`${id}-industry`} className="font-medium text-sm">
                行业
              </label>
              <Input
                id={`${id}-industry`}
                value={form.industry ?? ""}
                onChange={(event) => setForm({ industry: event.target.value })}
                placeholder="例如：餐饮、美妆、教育"
              />
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <label htmlFor={`${id}-logo`} className="font-medium text-sm">
              Logo URL
            </label>
            <Input
              id={`${id}-logo`}
              value={form.logoUrl ?? ""}
              onChange={(event) => setForm({ logoUrl: event.target.value })}
              placeholder="https://..."
            />
          </div>
          {fields.map((field) => (
            <div key={field.key} className="flex flex-col gap-2">
              <label htmlFor={`${id}-${field.key}`} className="font-medium text-sm">
                {field.label}
              </label>
              <Textarea
                id={`${id}-${field.key}`}
                value={String(form[field.key] ?? "")}
                onChange={(event) => setForm({ [field.key]: event.target.value })}
                placeholder={field.placeholder}
                className="min-h-20 resize-y"
              />
            </div>
          ))}
        </div>
        <SheetFooter className="flex-row justify-end">
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button disabled={!form.name.trim() || save.isPending} onClick={() => save.mutate(form)}>
            {save.isPending ? "保存中…" : "保存资料"}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function BrandCard({ profile, onEdit }: { profile: ContentBrandProfileVO; onEdit: () => void }) {
  const confirm = useBoolean()
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: () => contentStudioApi.deleteBrandProfile(profile.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["content-studio", "brand-profiles"] })
      toast.success("品牌资料已删除")
    }
  })
  const Icon = profile.kind === "personal_ip" ? UserRound : Building2

  return (
    <>
      <GlassCard interactive>
        <GlassCardBody className="flex flex-col gap-4">
          <div className="flex items-start justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <Icon />
              </div>
              <div className="min-w-0">
                <h2 className="truncate font-medium">{profile.name}</h2>
                <p className="text-muted-foreground text-xs">{profile.industry ?? "未设置行业"}</p>
              </div>
            </div>
            <NeonChip tone="violet" size="sm">
              {KIND_LABELS[profile.kind]}
            </NeonChip>
          </div>
          <p className="line-clamp-2 min-h-10 text-muted-foreground text-sm">
            {profile.positioning || "尚未填写品牌定位"}
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={onEdit}>
              <Pencil />
              编辑
            </Button>
            <Button variant="ghost" size="sm" onClick={confirm.onTrue}>
              <Trash2 />
              删除
            </Button>
          </div>
        </GlassCardBody>
      </GlassCard>
      <ConfirmDialog
        open={confirm.value}
        onOpenChange={confirm.setValue}
        title="删除品牌资料"
        description={`确定删除「${profile.name}」吗？已绑定项目可能无法继续使用该资料。`}
        confirmText="删除"
        variant="destructive"
        onConfirm={() => remove.mutate()}
      />
    </>
  )
}

export default function StudioBrandsPage() {
  const editor = useBoolean()
  const [editing, setEditing] = useState<ContentBrandProfileVO | null>(null)
  const { data, isLoading } = useContentBrandProfiles()
  const profiles = data?.list ?? []

  function openCreate() {
    setEditing(null)
    editor.onTrue()
  }

  function openEdit(profile: ContentBrandProfileVO) {
    setEditing(profile)
    editor.onTrue()
  }

  return (
    <div className="relative h-full overflow-y-auto">
      <SectionHaze variant="violet" />
      <div className="relative mx-auto flex max-w-6xl flex-col gap-6 p-6">
        <header className="flex items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <h1 className="font-semibold text-xl">品牌 · IP 资料</h1>
            <p className="text-muted-foreground text-sm">
              维护可跨项目复用的品牌事实、表达与视觉约束。
            </p>
          </div>
          <GlowButton tone="violet" onClick={openCreate}>
            <Plus />
            新建资料
          </GlowButton>
        </header>
        {isLoading ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }, (_, index) => (
              <Skeleton key={`brand-${index}`} className="h-48 rounded-2xl" />
            ))}
          </div>
        ) : profiles.length === 0 ? (
          <GlassCard glow="none">
            <Empty className="min-h-72">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <Building2 />
                </EmptyMedia>
                <EmptyTitle>还没有品牌 / IP 资料</EmptyTitle>
                <EmptyDescription>
                  先建立一份资料，后续项目即可复用品牌定位与规则。
                </EmptyDescription>
              </EmptyHeader>
              <EmptyContent>
                <Button onClick={openCreate}>
                  <Plus />
                  新建资料
                </Button>
              </EmptyContent>
            </Empty>
          </GlassCard>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {profiles.map((profile) => (
              <BrandCard key={profile.id} profile={profile} onEdit={() => openEdit(profile)} />
            ))}
          </div>
        )}
      </div>
      <BrandProfileSheet open={editor.value} onOpenChange={editor.setValue} profile={editing} />
    </div>
  )
}
