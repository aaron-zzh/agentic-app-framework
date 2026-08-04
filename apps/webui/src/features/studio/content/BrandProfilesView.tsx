/**
 * Content Studio 品牌/IP 稳定身份与不可变版本管理。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean, useSetState } from "@aaf/hooks"
import { Building2, FileClock, Pencil, Plus, Send, Trash2, UserRound } from "lucide-react"
import { useEffect, useId, useState } from "react"
import { GlassCard, GlassCardBody, GlowButton, NeonChip, SectionHaze } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
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
import type { AigcBrandProfile, AigcBrandProfileKind } from "@/lib/api/rest/ai/aigc"
import {
  useAigcBrandProfiles,
  useAigcBrandVersions,
  useCreateAigcBrandProfile,
  useCreateAigcBrandVersion,
  useDeleteAigcBrandProfile,
  usePublishAigcBrandVersion,
  useUpdateAigcBrandProfile
} from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"

const KIND_LABELS: Record<AigcBrandProfileKind, string> = {
  enterprise: "企业品牌",
  sub_brand: "子品牌",
  product_line: "产品线",
  personal_ip: "个人 IP"
}

interface BrandProfileForm {
  name: string
  kind: AigcBrandProfileKind
  industry: string
  status: string
}

interface BrandVersionForm {
  positioning: string
  audience: string
  toneOfVoice: string
  visualStyle: string
  disclaimer: string
  forbiddenItems: string
}

const EMPTY_PROFILE_FORM: BrandProfileForm = {
  name: "",
  kind: "enterprise",
  industry: "",
  status: "active"
}

const EMPTY_VERSION_FORM: BrandVersionForm = {
  positioning: "",
  audience: "",
  toneOfVoice: "",
  visualStyle: "",
  disclaimer: "",
  forbiddenItems: ""
}

function optional(value: string): string | undefined {
  return value.trim() || undefined
}

function BrandProfileSheet({
  open,
  onOpenChange,
  profile
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  profile: AigcBrandProfile | null
}) {
  const id = useId()
  const { state: form, setState: setForm } = useSetState<BrandProfileForm>(EMPTY_PROFILE_FORM)
  const createProfile = useCreateAigcBrandProfile()
  const updateProfile = useUpdateAigcBrandProfile()
  const isPending = createProfile.isPending || updateProfile.isPending

  useEffect(() => {
    if (!open) return
    setForm(
      profile
        ? {
            name: profile.name,
            kind: profile.kind,
            industry: profile.industry ?? "",
            status: profile.status
          }
        : EMPTY_PROFILE_FORM
    )
  }, [open, profile, setForm])

  function save() {
    if (!form.name.trim()) return
    if (profile) {
      updateProfile.mutate(
        {
          id: profile.id,
          data: {
            name: form.name.trim(),
            kind: form.kind,
            industry: optional(form.industry),
            status: form.status,
            expectedVersion: profile.version
          }
        },
        {
          onSuccess: () => {
            notify.success("品牌身份已更新")
            onOpenChange(false)
          }
        }
      )
      return
    }
    createProfile.mutate(
      {
        name: form.name.trim(),
        kind: form.kind,
        industry: optional(form.industry),
        status: form.status
      },
      {
        onSuccess: () => {
          notify.success("品牌身份已创建，请继续创建并发布资料版本")
          onOpenChange(false)
        }
      }
    )
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="overflow-y-auto sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>{profile ? "编辑品牌 / IP 身份" : "新建品牌 / IP 身份"}</SheetTitle>
          <SheetDescription>
            稳定身份只保存名称、类型和行业；创作规则由不可变版本承载。
          </SheetDescription>
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
                onValueChange={(value) => value && setForm({ kind: value as AigcBrandProfileKind })}
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
                value={form.industry}
                onChange={(event) => setForm({ industry: event.target.value })}
                placeholder="例如：餐饮、美妆、教育"
              />
            </div>
          </div>
        </div>
        <SheetFooter className="flex-row justify-end">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button type="button" disabled={!form.name.trim() || isPending} onClick={save}>
            {isPending ? "保存中…" : "保存身份"}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function BrandVersionSheet({
  profile,
  onOpenChange
}: {
  profile: AigcBrandProfile | null
  onOpenChange: (open: boolean) => void
}) {
  const id = useId()
  const { state: form, setState: setForm } = useSetState<BrandVersionForm>(EMPTY_VERSION_FORM)
  const { data: versions = [], isLoading } = useAigcBrandVersions(profile?.id ?? null)
  const createVersion = useCreateAigcBrandVersion()
  const publishVersion = usePublishAigcBrandVersion()

  useEffect(() => {
    if (profile) setForm(EMPTY_VERSION_FORM)
  }, [profile, setForm])

  function createDraft() {
    if (!profile) return
    createVersion.mutate(
      {
        profileId: profile.id,
        data: {
          expectedProfileVersion: profile.version,
          positioning: optional(form.positioning),
          audience: optional(form.audience),
          toneOfVoice: optional(form.toneOfVoice),
          visualStyle: optional(form.visualStyle),
          disclaimer: optional(form.disclaimer),
          forbiddenItems: optional(form.forbiddenItems),
          mediaVersionIds: [],
          documentVersionIds: []
        }
      },
      {
        onSuccess: () => {
          setForm(EMPTY_VERSION_FORM)
          notify.success("品牌资料草稿版本已创建")
        }
      }
    )
  }

  return (
    <Sheet open={profile !== null} onOpenChange={onOpenChange}>
      <SheetContent className="overflow-y-auto sm:max-w-2xl">
        <SheetHeader>
          <SheetTitle>{profile?.name ?? "品牌资料"} · 版本</SheetTitle>
          <SheetDescription>
            草稿发布后成为项目可绑定的不可变 BrandProfileVersion。
          </SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-6 px-4 pb-4">
          <section className="flex flex-col gap-3 rounded-xl border p-4">
            <div>
              <h3 className="font-medium">创建资料草稿</h3>
              <p className="text-muted-foreground text-sm">
                媒体与文档引用可在后续专业编辑器中补充。
              </p>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="flex flex-col gap-2">
                <label htmlFor={`${id}-positioning`} className="text-sm">
                  品牌定位
                </label>
                <Input
                  id={`${id}-positioning`}
                  value={form.positioning}
                  onChange={(event) => setForm({ positioning: event.target.value })}
                />
              </div>
              <div className="flex flex-col gap-2">
                <label htmlFor={`${id}-audience`} className="text-sm">
                  目标受众
                </label>
                <Input
                  id={`${id}-audience`}
                  value={form.audience}
                  onChange={(event) => setForm({ audience: event.target.value })}
                />
              </div>
              <div className="flex flex-col gap-2">
                <label htmlFor={`${id}-tone`} className="text-sm">
                  表达语气
                </label>
                <Input
                  id={`${id}-tone`}
                  value={form.toneOfVoice}
                  onChange={(event) => setForm({ toneOfVoice: event.target.value })}
                />
              </div>
              <div className="flex flex-col gap-2">
                <label htmlFor={`${id}-visual`} className="text-sm">
                  视觉风格
                </label>
                <Input
                  id={`${id}-visual`}
                  value={form.visualStyle}
                  onChange={(event) => setForm({ visualStyle: event.target.value })}
                />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <label htmlFor={`${id}-disclaimer`} className="text-sm">
                必要声明
              </label>
              <Textarea
                id={`${id}-disclaimer`}
                value={form.disclaimer}
                onChange={(event) => setForm({ disclaimer: event.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <label htmlFor={`${id}-forbidden`} className="text-sm">
                禁用项
              </label>
              <Textarea
                id={`${id}-forbidden`}
                value={form.forbiddenItems}
                onChange={(event) => setForm({ forbiddenItems: event.target.value })}
              />
            </div>
            <Button
              type="button"
              className="self-end"
              disabled={!profile || createVersion.isPending}
              onClick={createDraft}
            >
              <Plus /> {createVersion.isPending ? "创建中…" : "创建草稿版本"}
            </Button>
          </section>

          <section className="flex flex-col gap-3">
            <h3 className="font-medium">版本历史</h3>
            {isLoading ? (
              <Skeleton className="h-28 w-full" />
            ) : versions.length === 0 ? (
              <p className="rounded-xl border border-dashed p-4 text-center text-muted-foreground text-sm">
                暂无品牌资料版本
              </p>
            ) : (
              versions.map((version) => (
                <div
                  key={version.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border p-3"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="font-medium text-sm">版本 {version.versionNo}</p>
                      <Badge variant={version.status === "published" ? "secondary" : "outline"}>
                        {version.status}
                      </Badge>
                    </div>
                    <p className="mt-1 text-muted-foreground text-xs">
                      {version.positioning ||
                        version.audience ||
                        version.toneOfVoice ||
                        "尚未填写资料摘要"}
                    </p>
                  </div>
                  {version.status === "draft" && profile ? (
                    <Button
                      type="button"
                      size="sm"
                      disabled={publishVersion.isPending}
                      onClick={() =>
                        publishVersion.mutate(
                          {
                            profileId: profile.id,
                            versionId: version.id,
                            expectedProfileVersion: profile.version,
                            expectedVersion: version.version
                          },
                          {
                            onSuccess: () => {
                              notify.success("品牌资料版本已发布并设为当前版本")
                              onOpenChange(false)
                            }
                          }
                        )
                      }
                    >
                      <Send /> 发布
                    </Button>
                  ) : null}
                </div>
              ))
            )}
          </section>
        </div>
      </SheetContent>
    </Sheet>
  )
}

function BrandCard({
  profile,
  onEdit,
  onVersions
}: {
  profile: AigcBrandProfile
  onEdit: () => void
  onVersions: () => void
}) {
  const confirm = useBoolean()
  const remove = useDeleteAigcBrandProfile()
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
          <p className="text-muted-foreground text-sm">
            {profile.currentVersionId
              ? `当前发布版本 #${profile.currentVersionId}`
              : "尚未发布品牌资料版本"}
          </p>
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onVersions}>
              <FileClock />
              版本管理
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={onEdit}>
              <Pencil />
              编辑身份
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={confirm.onTrue}>
              <Trash2 />
              删除
            </Button>
          </div>
        </GlassCardBody>
      </GlassCard>
      <ConfirmDialog
        open={confirm.value}
        onOpenChange={confirm.setValue}
        title="删除品牌身份"
        description={`确定删除「${profile.name}」吗？存在品牌版本时后端会拒绝删除。`}
        confirmText="删除"
        variant="destructive"
        onConfirm={() =>
          remove.mutate(profile.id, { onSuccess: () => notify.success("品牌身份已删除") })
        }
      />
    </>
  )
}

export function BrandProfilesView() {
  const editor = useBoolean()
  const [editing, setEditing] = useState<AigcBrandProfile | null>(null)
  const [versionProfile, setVersionProfile] = useState<AigcBrandProfile | null>(null)
  const { data, isLoading } = useAigcBrandProfiles()
  const profiles = data?.list ?? []

  function openCreate() {
    setEditing(null)
    editor.onTrue()
  }

  return (
    <div className="relative h-full overflow-y-auto">
      <SectionHaze variant="violet" />
      <div className="relative mx-auto flex max-w-6xl flex-col gap-6 p-6">
        <header className="flex items-center justify-between gap-4">
          <div>
            <h1 className="font-semibold text-xl">品牌 · IP 资料</h1>
            <p className="text-muted-foreground text-sm">稳定身份与不可变发布版本分离管理。</p>
          </div>
          <GlowButton tone="violet" onClick={openCreate}>
            <Plus />
            新建身份
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
                <EmptyTitle>还没有品牌 / IP 身份</EmptyTitle>
                <EmptyDescription>先建立稳定身份，再创建并发布品牌资料版本。</EmptyDescription>
              </EmptyHeader>
              <EmptyContent>
                <Button type="button" onClick={openCreate}>
                  <Plus />
                  新建身份
                </Button>
              </EmptyContent>
            </Empty>
          </GlassCard>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {profiles.map((profile) => (
              <BrandCard
                key={profile.id}
                profile={profile}
                onEdit={() => {
                  setEditing(profile)
                  editor.onTrue()
                }}
                onVersions={() => setVersionProfile(profile)}
              />
            ))}
          </div>
        )}
      </div>
      <BrandProfileSheet open={editor.value} onOpenChange={editor.setValue} profile={editing} />
      <BrandVersionSheet
        profile={versionProfile}
        onOpenChange={(open) => {
          if (!open) setVersionProfile(null)
        }}
      />
    </div>
  )
}
